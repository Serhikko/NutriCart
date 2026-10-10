package com.nutricart.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nutricart.app.domain.model.MealSlot
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberBrushes
import com.nutricart.app.ui.ember.EmberCard
import com.nutricart.app.ui.ember.EmberIcon
import com.nutricart.app.ui.ember.EmberIcons
import com.nutricart.app.ui.ember.EmberShapes
import com.nutricart.app.ui.ember.EntranceKind
import com.nutricart.app.ui.ember.MealTiles
import com.nutricart.app.ui.ember.emberEntrance
import com.nutricart.app.ui.ember.rememberFirstOpen
import org.junit.Test

/**
 * The Ember foundation (wave 0): every glyph, the type scale and the palette, plus the entrance
 * motion as frames. Compare with app-design/shots/icons-{light,dark}.png and the web's tokens.
 */
class EmberFoundationScreenshots(variant: Variant) : ScreenshotTest(variant) {

    @Composable
    private fun Page(content: @Composable () -> Unit) {
        Column(
            Modifier.fillMaxSize().background(Ember.colors.bg).padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }

    @Test
    fun icons() = shoot("ember-icons") {
        Page {
            val c = Ember.colors
            Text("Icons", style = Ember.type.largeTitle)
            EmberCard {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EmberIcons.entries.forEach { icon ->
                        Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            EmberIcon(icon, null, tint = c.label)
                            Text(
                                icon.name, style = Ember.type.caption.copy(fontSize = Ember.type.tab.fontSize * .8f),
                                color = c.label2, maxLines = 1, textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
            EmberCard {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    EmberIcon(EmberIcons.Day, null, size = 25.dp, brush = EmberBrushes.emberIcon(c))
                    EmberIcon(EmberIcons.Plus, null, size = 30.dp, brush = EmberBrushes.emberIcon(c))
                    EmberIcon(EmberIcons.Flame, null, size = 20.dp, brush = EmberBrushes.emberIcon(c))
                    EmberIcon(EmberIcons.Check, null, size = 20.dp, brush = EmberBrushes.emberIcon(c))
                    EmberIcon(EmberIcons.Star, null, size = 22.dp, tint = c.tint, filled = true)
                    EmberIcon(EmberIcons.Left, null, size = 16.dp, tint = c.tint)
                    MealSlot.entries.forEach { slot ->
                        Box(Modifier.size(34.dp).background(MealTiles.brush(slot), EmberShapes.tileIcon), contentAlignment = Alignment.Center) {
                            EmberIcon(MealTiles.icon(slot), null, size = 19.dp, tint = c.onAccent)
                        }
                    }
                }
            }
        }
    }

    @Test
    @KeyScreen
    fun type() = shoot("ember-type") {
        Page {
            val t = Ember.type
            val c = Ember.colors
            Text("Субота, 10 жовтня", style = t.subhead, color = c.tint)
            Text("Today · Сьогодні", style = t.largeTitle)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("855", style = t.heroNumeral, color = c.label)
                Text("1,230", style = t.stat.copy(brush = EmberBrushes.emberText(c, 300f, 0f)))
                Text("ккал", style = t.headline, color = c.label2)
            }
            Spec("title1 · Зʼїдено ґрунт їжак", t.title1)
            Spec("title2 · Food diary", t.title2)
            Spec("headline · Breakfast reminder", t.headline)
            Spec("body · Locked meals stay. 7 days within ±5% of your target, protein first.", t.body)
            Spec("callout · Вівсянка з бананом і волоськими горіхами", t.callout)
            Spec("footnote · 1 230 ккал − 9,7 г", t.footnote)
            Spec("caption · Mon Tue Wed", t.caption)
            Spec("tab · Today Plan Fridge Diary", t.tab)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    c.surface, c.surface2, c.surface3, c.fill, c.fill2, c.ink, c.tint, c.danger, c.good,
                    c.ember1, c.ember2, c.ember3, c.protein, c.fat, c.carbs, c.water, c.weight, c.label4,
                ).forEach { Swatch(it) }
            }
        }
    }

    @Composable
    private fun Spec(text: String, style: TextStyle) = Text(text, style = style, color = Ember.colors.label)

    @Composable
    private fun Swatch(color: Color) =
        Box(Modifier.size(32.dp).background(Ember.colors.bg).background(color, EmberShapes.tileIcon))

    @Composable
    private fun EntrancePage() {
        val first = rememberFirstOpen("foundation")
        Page {
            Text("Entrances", Modifier.emberEntrance(0, EntranceKind.Title, first), style = Ember.type.largeTitle)
            Text("Saturday 10 October", Modifier.emberEntrance(0, EntranceKind.FadeUp, first), style = Ember.type.subhead, color = Ember.colors.tint)
            repeat(4) { i ->
                EmberCard(Modifier.fillMaxWidth().emberEntrance(i + 1, first = first)) {
                    Text("Card ${i + 1}", style = Ember.type.headline)
                }
            }
        }
    }

    @Test
    fun entranceFirstOpen() = shootFrames("ember-entrance-first") { EntrancePage() }

    @Test
    fun entranceRevisit() = shootFrames("ember-entrance-revisit", times = listOf(60, 150, 300), firstOpen = false) {
        EntrancePage()
    }
}
