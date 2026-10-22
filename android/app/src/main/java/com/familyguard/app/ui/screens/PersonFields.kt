package com.familyguard.app.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Gender, birth date and phone: needed for contact names ("Мама", "Старший брат") and quick dial. */
data class PersonData(
    val gender: String = "",       // "male" | "female"
    val birthDate: String = "",    // ISO yyyy-MM-dd, as the API expects
    val phone: String = ""
) {
    val isComplete: Boolean
        get() = gender.isNotEmpty() && birthDate.isNotEmpty() && phone.filter { it.isDigit() }.length >= 9
}

private val shownDate = DateTimeFormatter.ofPattern("dd.MM.yyyy")

@Composable
fun PersonFields(value: PersonData, onChange: (PersonData) -> Unit) {
    val context = LocalContext.current

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Пол:", modifier = Modifier.padding(end = 8.dp))
        RadioButton(selected = value.gender == "male", onClick = { onChange(value.copy(gender = "male")) })
        Text("Мужской", modifier = Modifier.padding(end = 12.dp))
        RadioButton(selected = value.gender == "female", onClick = { onChange(value.copy(gender = "female")) })
        Text("Женский")
    }

    Spacer(modifier = Modifier.height(8.dp))

    val date = value.birthDate.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    fun pickDate() {
        val start = date ?: LocalDate.now().minusYears(10)
        DatePickerDialog(context, { _, y, m, d ->
            onChange(value.copy(birthDate = LocalDate.of(y, m + 1, d).toString()))
        }, start.year, start.monthValue - 1, start.dayOfMonth).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }
    // A read-only field that opens the date picker
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = date?.format(shownDate) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Дата рождения") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Box(Modifier.matchParentSize().clickable { pickDate() })
    }

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        value = value.phone,
        onValueChange = { onChange(value.copy(phone = it)) },
        label = { Text("Телефон") },
        placeholder = { Text("+998901234567") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        singleLine = true
    )
}
