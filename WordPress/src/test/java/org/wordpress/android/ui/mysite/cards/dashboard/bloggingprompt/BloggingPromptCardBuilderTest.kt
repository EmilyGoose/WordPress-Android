package org.wordpress.android.ui.mysite.cards.dashboard.bloggingprompt

import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.wordpress.android.BaseUnitTest
import org.wordpress.android.R.attr
import org.wordpress.android.R.plurals
import org.wordpress.android.models.bloggingprompts.BloggingPrompt
import org.wordpress.android.models.bloggingprompts.BloggingPromptRespondent
import org.wordpress.android.ui.avatars.TrainOfAvatarsItem.AvatarItem
import org.wordpress.android.ui.avatars.TrainOfAvatarsItem.TrailingLabelTextItem
import org.wordpress.android.ui.mysite.MySiteCardAndItem.Card.DashboardCards.DashboardCard.BloggingPromptCard.BloggingPromptCardWithData
import org.wordpress.android.ui.mysite.MySiteCardAndItemBuilderParams.BloggingPromptCardBuilderParams
import org.wordpress.android.ui.mysite.cards.dashboard.bloggingprompts.BloggingPromptCardBuilder
import org.wordpress.android.ui.utils.UiString.UiStringPluralRes
import org.wordpress.android.ui.utils.UiString.UiStringText

private const val PROMPT_TITLE = "Test Prompt"
private const val NUMBER_OF_RESPONDENTS = 5

private val RESPONDENTS_IN_CARD = listOf(
        AvatarItem(
                1,
                "http://avatar1.url"
        ), AvatarItem(
        2,
        "http://avatar2.url"
), AvatarItem(
        3,
        "http://avatar3.url"
), TrailingLabelTextItem(
        UiStringPluralRes(
                plurals.my_site_blogging_prompt_card_number_of_answers,
                NUMBER_OF_RESPONDENTS
        ), attr.colorPrimary
)
)

private val RESPONDENTS = listOf(
        BloggingPromptRespondent(
                1,
                "http://avatar1.url"
        ),
        BloggingPromptRespondent(
                2,
                "http://avatar2.url"
        ),
        BloggingPromptRespondent(
                3,
                "http://avatar3.url"
        ),
        BloggingPromptRespondent(
                4,
                "http://avatar4.url"
        ),
        BloggingPromptRespondent(
                5,
                "http://avatar5.url"
        )
)

/* ktlint-disable max-line-length */
@RunWith(MockitoJUnitRunner::class)
class BloggingPromptCardBuilderTest : BaseUnitTest() {
    private lateinit var builder: BloggingPromptCardBuilder
    private val bloggingPrompt = BloggingPrompt(
            id = 1234,
            text = PROMPT_TITLE,
            content = "<!-- wp:pullquote -->\n" +
                    "<figure class=\"wp-block-pullquote\"><blockquote><p>You have 15 minutes to address the whole world live (on television or radio — choose your format). What would you say?</p><cite>(courtesy of plinky.com)</cite></blockquote></figure>\n" +
                    "<!-- /wp:pullquote -->",
            respondents = RESPONDENTS
    )

    @Before
    fun setUp() {
        builder = BloggingPromptCardBuilder()
    }

    @Test
    fun `given blogging prompt, when card is built, then return card`() {
        val statCard = buildBloggingPromptCard(bloggingPrompt)

        assertThat(statCard).isNotNull()
    }

    @Test
    fun `given blogging prompt, when card is built, then return matching card`() {
        val statCard = buildBloggingPromptCard(bloggingPrompt)

        assertThat(statCard).isEqualTo(bloggingPromptCard)
    }

    @Test
    fun `given no blogging prompt, when card is built, then return null`() {
        val statCard = buildBloggingPromptCard(null)

        assertThat(statCard).isNull()
    }

    private fun buildBloggingPromptCard(bloggingPrompt: BloggingPrompt?) = builder.build(
            BloggingPromptCardBuilderParams(bloggingPrompt, onShareClick, onAnswerClick)
    )

    private val onShareClick: (message: String) -> Unit = { }

    private val onAnswerClick: () -> Unit = { }

    private val bloggingPromptCard = BloggingPromptCardWithData(
            prompt = UiStringText(PROMPT_TITLE),
            respondents = RESPONDENTS_IN_CARD,
            numberOfAnswers = NUMBER_OF_RESPONDENTS,
            false,
            onShareClick = onShareClick,
            onAnswerClick = onAnswerClick
    )
}
