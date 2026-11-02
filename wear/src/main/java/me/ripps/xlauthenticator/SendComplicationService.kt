package me.ripps.xlauthenticator

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

/** A watch-face slot that sends the 2FA code when tapped. Static: it never needs updating. */
class SendComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType)

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type)

    private fun build(type: ComplicationType): ComplicationData? {
        val icon = Icon.createWithResource(this, R.drawable.ic_send)
        val description = PlainComplicationText.Builder(getString(R.string.complication_label)).build()
        val tap = PendingIntent.getActivity(
            this, 0,
            Intent(this, SendActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                PlainComplicationText.Builder("2FA").build(),
                description,
            ).setMonochromaticImage(MonochromaticImage.Builder(icon).build()).setTapAction(tap).build()

            ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(
                MonochromaticImage.Builder(icon).build(),
                description,
            ).setTapAction(tap).build()

            ComplicationType.SMALL_IMAGE -> SmallImageComplicationData.Builder(
                SmallImage.Builder(icon, SmallImageType.ICON).build(),
                description,
            ).setTapAction(tap).build()

            else -> null
        }
    }
}
