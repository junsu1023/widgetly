package com.example.widgetly.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PairingScreen(viewModel: PairingViewModel) {
    val state by viewModel.state.collectAsState()
    var enteredCode by remember { mutableStateOf("") }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("커플/친구 연결")

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.createCode(ConnectionType.COUPLE) }) {
                    Text("커플 코드 생성")
                }
                Button(onClick = { viewModel.createCode(ConnectionType.FRIEND) }) {
                    Text("친구 코드 생성")
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = enteredCode,
                    onValueChange = { enteredCode = it },
                    label = { Text("상대방 코드 입력") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(onClick = { viewModel.redeem(enteredCode) }) {
                Text("연결하기")
            }

            when (val current = state) {
                PairingUiState.Idle -> {}
                is PairingUiState.CodeCreated -> Text("생성된 코드: ${current.code}")
                is PairingUiState.Connected -> Text("연결되었습니다: ${current.connectionId}")
                is PairingUiState.Error -> Text("오류: ${current.message}")
            }
        }
    }
}
