package com.nutricart.app.ui.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutricart.app.data.local.entity.FoodProductEntity
import com.nutricart.app.data.repository.DiaryRepository
import com.nutricart.app.data.repository.FoodRepository
import com.nutricart.app.domain.model.MealSlot
import com.nutricart.app.scanner.BarcodeScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One-shot messages of the barcode flow, shown as a snackbar and cleared. */
enum class ScanMessage { PRODUCT_NOT_FOUND, OFFLINE, SCANNER_FAILED }

data class FoodSearchUiState(
    val query: String = "",
    /** True when the user tried to search with fewer than 2 characters. */
    val queryTooShort: Boolean = false,
    val scanMessage: ScanMessage? = null,
    val searching: Boolean = false,
    /** False until the first search, so we don't show "nothing found" too early. */
    val searched: Boolean = false,
    val results: List<FoodProductEntity> = emptyList(),
    /** True when the results came from the local cache (no internet). */
    val offline: Boolean = false,
    /** The product the user tapped — non-null shows the amount dialog. */
    val selected: FoodProductEntity? = null,
    /** Flips to true after saving; the screen then navigates back. */
    val logged: Boolean = false,
)

@HiltViewModel
class FoodSearchViewModel @Inject constructor(
    private val foodRepository: FoodRepository,
    private val diaryRepository: DiaryRepository,
    private val barcodeScanner: BarcodeScanner, // interface — Hilt injects the ML Kit one
    savedStateHandle: SavedStateHandle, // navigation arguments arrive here
) : ViewModel() {

    /** Which meal and which day this search will log into (from the route). */
    val mealSlot: MealSlot = MealSlot.valueOf(checkNotNull(savedStateHandle["slot"]))
    private val epochDay: Long = checkNotNull(savedStateHandle["epochDay"])

    private val _uiState = MutableStateFlow(FoodSearchUiState())
    val uiState: StateFlow<FoodSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun setQuery(text: String) =
        _uiState.update { it.copy(query = text, queryTooShort = false) }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.length < 2) {
            // One letter would match half the database — tell the user instead
            // of silently doing nothing.
            _uiState.update { it.copy(queryTooShort = true) }
            return
        }
        // Cancel the previous request: a slow old response must never
        // overwrite the results of a newer search.
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(searching = true, queryTooShort = false) }
            val result = foodRepository.search(query)
            _uiState.update {
                it.copy(
                    searching = false,
                    searched = true,
                    results = result.products,
                    offline = result.offline,
                )
            }
        }
    }

    fun select(product: FoodProductEntity?) = _uiState.update { it.copy(selected = product) }

    private var scanning = false

    /** Opens the system scanner; a found product goes straight to the amount dialog. */
    fun scanBarcode() {
        if (scanning) return // the scanner UI takes a moment — ignore double-taps
        scanning = true
        viewModelScope.launch {
            try {
                val barcode = barcodeScanner.scan() ?: return@launch // user cancelled
                _uiState.update { it.copy(searching = true) }
                val product = foodRepository.byBarcode(barcode)
                _uiState.update {
                    it.copy(
                        searching = false,
                        selected = product,
                        scanMessage = if (product == null) ScanMessage.PRODUCT_NOT_FOUND else null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                // The scan worked, the phone is just offline.
                _uiState.update {
                    it.copy(searching = false, scanMessage = ScanMessage.OFFLINE)
                }
            } catch (e: Exception) {
                // E.g. the scanner module is still downloading on first use.
                _uiState.update {
                    it.copy(searching = false, scanMessage = ScanMessage.SCANNER_FAILED)
                }
            } finally {
                scanning = false
            }
        }
    }

    fun clearScanMessage() = _uiState.update { it.copy(scanMessage = null) }

    /** Called by the amount dialog with the final grams (and portions, if used). */
    fun log(product: FoodProductEntity, grams: Double, servings: Double?) {
        // Close the dialog IMMEDIATELY, before the database write: while an
        // insert is in flight a still-open dialog would let a double-tap
        // insert the same entry twice.
        _uiState.update { it.copy(selected = null) }
        viewModelScope.launch {
            diaryRepository.logProduct(
                product = product,
                grams = grams,
                servings = servings,
                meal = mealSlot,
                epochDay = epochDay,
            )
            _uiState.update { it.copy(logged = true) }
        }
    }
}
