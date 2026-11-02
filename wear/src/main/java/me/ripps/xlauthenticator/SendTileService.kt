package me.ripps.xlauthenticator

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture

/** A tile with one big button: tap to send the 2FA code. */
class SendTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { completer ->
            completer.set(
                TileBuilders.Tile.Builder()
                    .setResourcesVersion(RESOURCES_VERSION)
                    .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout()))
                    .build()
            )
            "tile"
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { completer ->
            completer.set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())
            "resources"
        }

    private fun layout(): LayoutElementBuilders.LayoutElement {
        val launch = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(SendActivity::class.java.name)
                    .build()
            )
            .build()
        val button = LayoutElementBuilders.Box.Builder()
            .setWidth(dp(BUTTON_SIZE))
            .setHeight(dp(BUTTON_SIZE))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(ModifiersBuilders.Clickable.Builder().setId("send").setOnClick(launch).build())
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(BUTTON_COLOR))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(BUTTON_SIZE / 2)).build())
                            .build()
                    )
                    .build()
            )
            .addContent(text("Send 2FA", 20f, TEXT_ON_BUTTON))
            .build()
        return LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text("XIVLauncher", 14f, TEXT_SECONDARY))
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).build())
            .addContent(button)
            .build()
            .let { column ->
                LayoutElementBuilders.Box.Builder()
                    .setWidth(expand())
                    .setHeight(expand())
                    .addContent(column)
                    .build()
            }
    }

    private fun text(value: String, size: Float, color: Int) = LayoutElementBuilders.Text.Builder()
        .setText(value)
        .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(sp(size)).setColor(argb(color)).build())
        .build()

    private companion object {
        const val RESOURCES_VERSION = "1"
        const val BUTTON_SIZE = 110f
        const val BUTTON_COLOR = 0xFFA8C7FA.toInt()
        const val TEXT_ON_BUTTON = 0xFF062E6F.toInt()
        const val TEXT_SECONDARY = 0xFFC4C7C5.toInt()
    }
}
