/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.ui.core

import androidx.compose.state
import androidx.test.filters.SmallTest
import androidx.ui.core.input.FocusManager
import androidx.ui.input.CommitTextEditOp
import androidx.ui.input.DeleteSurroundingTextEditOp
import androidx.ui.input.EditOperation
import androidx.ui.input.FinishComposingTextEditOp
import androidx.ui.input.SetComposingRegionEditOp
import androidx.ui.input.SetComposingTextEditOp
import androidx.ui.input.SetSelectionEditOp
import androidx.ui.input.TextInputService
import androidx.ui.test.createComposeRule
import androidx.ui.test.doGesture
import androidx.ui.test.findByTag
import androidx.ui.test.sendClick
import androidx.ui.unit.PxPosition
import androidx.ui.unit.px
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.clearInvocations
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@SmallTest
@RunWith(JUnit4::class)
class TextFieldOnValueChangeStringTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    val onValueChange: (String) -> Unit = mock()

    lateinit var onEditCommandCallback: (List<EditOperation>) -> Unit

    @Before
    fun setUp() {
        val focusManager = mock<FocusManager>()
        val textInputService = mock<TextInputService>()
        val inputSessionToken = 10 // any positive number is fine.

        // Always give focus to the passed node.
        whenever(focusManager.requestFocus(any())).thenAnswer {
            (it.arguments[0] as FocusManager.FocusNode).onFocus()
        }
        whenever(textInputService.startInput(any(), any(), any(), any(), any()))
            .thenReturn(inputSessionToken)

        composeTestRule.setContent {
            FocusManagerAmbient.Provider(value = focusManager) {
                TextInputServiceAmbient.Provider(value = textInputService) {
                    TestTag(tag = "textField") {
                        val state = state { "abcde" }
                        TextField(
                            value = state.value,
                            onValueChange = {
                                state.value = it
                                onValueChange(it)
                            }
                        )
                    }
                }
            }
        }

        // Perform click to focus in.
        findByTag("textField")
            .doGesture { sendClick(PxPosition(1.px, 1.px)) }

        composeTestRule.runOnUiThread {
            // Verify startInput is called and capture the callback.
            val onEditCommandCaptor = argumentCaptor<(List<EditOperation>) -> Unit>()
            verify(textInputService, times(1)).startInput(
                initModel = any(),
                keyboardType = any(),
                imeAction = any(),
                onEditCommand = onEditCommandCaptor.capture(),
                onImeActionPerformed = any()
            )
            assertThat(onEditCommandCaptor.allValues.size).isEqualTo(1)
            onEditCommandCallback = onEditCommandCaptor.firstValue
            assertThat(onEditCommandCallback).isNotNull()

            clearInvocations(onValueChange)
        }
    }

    private fun performEditOperation(op: EditOperation) {
        arrayOf(listOf(op)).forEach {
            composeTestRule.runOnUiThread { onEditCommandCallback(it) }
        }
    }

    @Test
    fun commitText_onValueChange_call_once() {
        // Committing text should be reported as value change
        performEditOperation(CommitTextEditOp("ABCDE", 1))
        composeTestRule.runOnIdleCompose {
            verify(onValueChange, times(1)).invoke(eq("ABCDEabcde"))
        }
    }

    @Test
    fun setComposingRegion_onValueChange_never_call() {
        // Composition conversion is not counted as a value change in string text field.
        performEditOperation(SetComposingRegionEditOp(0, 5))
        composeTestRule.runOnIdleCompose { verify(onValueChange, never()).invoke(any()) }
    }

    @Test
    fun setCompsingText_onValueChange_call_once() {
        performEditOperation(SetComposingTextEditOp("ABCDE", 1))
        composeTestRule.runOnIdleCompose { verify(onValueChange, times(1)).invoke("ABCDEabcde") }
    }

    @Test
    fun setSelection_onValueChange_never_call() {
        // Selection change is not counted as a value change in string text field
        performEditOperation(SetSelectionEditOp(1, 1))
        composeTestRule.runOnIdleCompose { verify(onValueChange, never()).invoke(any()) }
    }

    @Test
    fun finishComposition_onValueChange_never_call() {
        performEditOperation(SetComposingTextEditOp("ABCDE", 1))
        composeTestRule.runOnIdleCompose { verify(onValueChange, times(1)).invoke("ABCDEabcde") }

        // Finishing composition change is not counted as a value change in string text field.
        clearInvocations(onValueChange)
        performEditOperation(FinishComposingTextEditOp())
        composeTestRule.runOnIdleCompose { verify(onValueChange, never()).invoke(any()) }
    }

    @Test
    fun deleteSurroundingText_onValueChange_call_once() {
        performEditOperation(DeleteSurroundingTextEditOp(0, 1))
        composeTestRule.runOnIdleCompose { verify(onValueChange, times(1)).invoke("bcde") }
    }
}
