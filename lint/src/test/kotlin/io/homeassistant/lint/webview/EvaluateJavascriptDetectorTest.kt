package io.homeassistant.lint.webview

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class EvaluateJavascriptDetectorTest {

    private val webViewStub = kotlin(
        """
        package android.webkit

        open class WebView {
            fun evaluateJavascript(script: String, resultCallback: ((String?) -> Unit)?) {}
        }
        """,
    ).indented()

    private val evaluateJavascriptUsageStub = kotlin(
        """
        package io.homeassistant.companion.android.frontend

        @RequiresOptIn
        @Retention(AnnotationRetention.BINARY)
        @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
        annotation class EvaluateJavascriptUsage
        """,
    ).indented()

    @Test
    fun `Given evaluateJavascript call without annotation then error is reported`() {
        lint().issues(EvaluateJavascriptDetector.ISSUE)
            .allowMissingSdk()
            .files(
                webViewStub,
                evaluateJavascriptUsageStub,
                kotlin(
                    """
                    package com.example

                    import android.webkit.WebView

                    fun doSomething(webView: WebView) {
                        webView.evaluateJavascript("alert('hello')") {}
                    }
                    """,
                ).indented(),
            )
            .run()
            .expect(
                """
                src/com/example/test.kt:6: Error: Usage of WebView.evaluateJavascript requires @EvaluateJavascriptUsage or @OptIn(EvaluateJavascriptUsage::class) on the enclosing function or class. [EvaluateJavascriptUsage]
                    webView.evaluateJavascript("alert('hello')") {}
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 error
                """.trimIndent(),
            )
    }

    @Test
    fun `Given evaluateJavascript call with @EvaluateJavascriptUsage on function then no error`() {
        lint().issues(EvaluateJavascriptDetector.ISSUE)
            .allowMissingSdk()
            .files(
                webViewStub,
                evaluateJavascriptUsageStub,
                kotlin(
                    """
                    package com.example

                    import android.webkit.WebView
                    import io.homeassistant.companion.android.frontend.EvaluateJavascriptUsage

                    @EvaluateJavascriptUsage
                    fun doSomething(webView: WebView) {
                        webView.evaluateJavascript("alert('hello')") {}
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    @Test
    fun `Given evaluateJavascript call with @OptIn on function then no error`() {
        lint().issues(EvaluateJavascriptDetector.ISSUE)
            .allowMissingSdk()
            .files(
                webViewStub,
                evaluateJavascriptUsageStub,
                kotlin(
                    """
                    package com.example

                    import android.webkit.WebView
                    import io.homeassistant.companion.android.frontend.EvaluateJavascriptUsage

                    @RequiresOptIn
                    @Retention(AnnotationRetention.BINARY)
                    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
                    annotation class OtherOptIn

                    @OptIn(OtherOptIn::class, EvaluateJavascriptUsage::class)
                    fun doSomething(webView: WebView) {
                        webView.evaluateJavascript("alert('hello')") {}
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    @Test
    fun `Given evaluateJavascript call with @EvaluateJavascriptUsage on class then no error`() {
        lint().issues(EvaluateJavascriptDetector.ISSUE)
            .allowMissingSdk()
            .files(
                webViewStub,
                evaluateJavascriptUsageStub,
                kotlin(
                    """
                    package com.example

                    import android.webkit.WebView
                    import io.homeassistant.companion.android.frontend.EvaluateJavascriptUsage

                    @EvaluateJavascriptUsage
                    class MyAction {
                        fun run(webView: WebView) {
                            webView.evaluateJavascript("alert('hello')") {}
                        }
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    @Test
    fun `Given evaluateJavascript call with @OptIn on class then no error`() {
        lint().issues(EvaluateJavascriptDetector.ISSUE)
            .allowMissingSdk()
            .files(
                webViewStub,
                evaluateJavascriptUsageStub,
                kotlin(
                    """
                    package com.example

                    import android.webkit.WebView
                    import io.homeassistant.companion.android.frontend.EvaluateJavascriptUsage

                    @OptIn(EvaluateJavascriptUsage::class)
                    class MyAction {
                        fun run(webView: WebView) {
                            webView.evaluateJavascript("alert('hello')") {}
                        }
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    @Test
    fun `Given evaluateJavascript call with inline @OptIn on expression then no error`() {
        lint().issues(EvaluateJavascriptDetector.ISSUE)
            .allowMissingSdk()
            .files(
                webViewStub,
                evaluateJavascriptUsageStub,
                kotlin(
                    """
                    package com.example

                    import android.webkit.WebView
                    import io.homeassistant.companion.android.frontend.EvaluateJavascriptUsage

                    fun WebView.doSomething() {
                        @OptIn(EvaluateJavascriptUsage::class)
                        evaluateJavascript("alert('hello')", null)
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    @Test
    fun `Given evaluateJavascript call with androidx @OptIn on function then no error`() {
        val androidxOptInStub = kotlin(
            """
            package androidx.annotation

            @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
            @Retention(AnnotationRetention.SOURCE)
            annotation class OptIn(vararg val markerClass: kotlin.reflect.KClass<out Annotation>)
            """,
        ).indented()

        lint().issues(EvaluateJavascriptDetector.ISSUE)
            .allowMissingSdk()
            .files(
                webViewStub,
                evaluateJavascriptUsageStub,
                androidxOptInStub,
                kotlin(
                    """
                    package com.example

                    import android.webkit.WebView
                    import androidx.annotation.OptIn
                    import io.homeassistant.companion.android.frontend.EvaluateJavascriptUsage

                    @OptIn(EvaluateJavascriptUsage::class)
                    fun doSomething(webView: WebView) {
                        webView.evaluateJavascript("alert('hello')") {}
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }
}
