package com.example.medicationreminder.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MedicationDoseInputTest {

    @Test
    fun normalizesAmountAndTrimsCanonicalAndDisplayUnits() {
        assertEquals(
            EditorDose("1.23 tablets", "1.23", "tablet"),
            resolveEditorDose(" 001.2300 ", " tablet ", " tablets ")
        )
    }

    @Test
    fun preservesDecimalPrecisionAndUsesPlainDecimalText() {
        val cases = listOf(
            "1000.000" to "1000",
            "0.0000000100" to "0.00000001",
            "12345678901234567890.12345678900" to
                "12345678901234567890.123456789"
        )

        for ((input, normalized) in cases) {
            assertEquals(
                "Input: $input",
                EditorDose("$normalized mg", normalized, "mg"),
                resolveEditorDose(input, "mg", "mg")
            )
        }
    }

    @Test
    fun usesLocalizedDisplayUnitWhileKeepingCanonicalUnit() {
        assertEquals(
            EditorDose("2 片", "2", "tablet"),
            resolveEditorDose("2.00", "tablet", " 片 ")
        )
    }

    @Test
    fun preservesCustomUnitWithoutConvertingAmount() {
        assertEquals(
            EditorDose("0.5 measuring scoops", "0.5", "measuring scoops"),
            resolveEditorDose("0.500", " measuring scoops ", " measuring scoops ")
        )
    }

    @Test
    fun rejectsBlankNewDose() {
        assertNull(resolveEditorDose("", "", ""))
        assertNull(resolveEditorDose(" \t ", " \n ", ""))
    }

    @Test
    fun rejectsIncompleteDoseEvenWhenLegacyTextExists() {
        val inputs = listOf(
            "1" to "",
            "1" to " \t ",
            "" to "mg",
            " \t " to "mg"
        )

        for (legacy in listOf<String?>(null, "Take one after breakfast")) {
            for ((amount, unit) in inputs) {
                assertNull(
                    "Amount='$amount', unit='$unit', legacy='$legacy'",
                    resolveEditorDose(amount, unit, "mg", legacy)
                )
            }
        }
    }

    @Test
    fun rejectsInvalidZeroAndNegativeAmountsEvenWhenLegacyTextExists() {
        val invalidAmounts = listOf(
            "abc",
            "1,5",
            "1 2",
            "1mg",
            "NaN",
            "Infinity",
            "0",
            "0.000",
            "-0.0",
            "-1",
            "-0.5"
        )

        for (legacy in listOf<String?>(null, "Take one after breakfast")) {
            for (amount in invalidAmounts) {
                assertNull(
                    "Amount='$amount', legacy='$legacy'",
                    resolveEditorDose(amount, "mg", "mg", legacy)
                )
            }
        }
    }

    @Test
    fun preservesUnchangedLegacyTextExactly() {
        val legacy = "  Take half a tablet after breakfast  "

        assertEquals(
            EditorDose(legacy, null, null),
            resolveEditorDose("", "", "tablet", legacy)
        )
    }

    @Test
    fun treatsWhitespaceOnlyInputsAsUnchangedLegacyDose() {
        val legacy = "One scoop with food"

        assertEquals(
            EditorDose(legacy, null, null),
            resolveEditorDose(" \t ", " \n ", "scoop", legacy)
        )
    }

    @Test
    fun doesNotParseNumericLookingLegacyText() {
        val legacy = "2 mg"

        assertEquals(
            EditorDose(legacy, null, null),
            resolveEditorDose("", "", "mg", legacy)
        )
    }

    @Test
    fun explicitReplacementCreatesKnownDoseInsteadOfKeepingLegacyText() {
        assertEquals(
            EditorDose("2.5 mg", "2.5", "mg"),
            resolveEditorDose(
                amountInput = " 2.500 ",
                unitInput = " mg ",
                displayUnit = "mg",
                legacyDosageText = "Take half a tablet after breakfast"
            )
        )
    }
}
