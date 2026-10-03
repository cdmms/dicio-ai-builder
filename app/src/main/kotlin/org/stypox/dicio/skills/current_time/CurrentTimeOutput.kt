package org.stypox.dicio.skills.current_time

import com.memeable.dicioai.R

import org.dicio.skill.context.SkillContext
import org.stypox.dicio.io.graphical.HeadlineSpeechSkillOutput
import org.stypox.dicio.util.getString

class CurrentTimeOutput(
    private val timeStr: String,
) : HeadlineSpeechSkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String =
        ctx.getString(R.string.skill_time_current_time, timeStr)
}
