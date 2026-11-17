package com.timewallet.mvp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timewallet.mvp.ui.theme.WalletDangerBg
import com.timewallet.mvp.ui.theme.WalletDangerText
import com.timewallet.mvp.ui.theme.WalletInk
import com.timewallet.mvp.ui.theme.WalletMuted

/**
 * v1.0.11: quản lý dữ liệu (xuất JSON, nạp dữ liệu mẫu, xóa toàn bộ) chuyển từ màn Nhật ký sang menu phụ.
 * Hành vi giữ nguyên: xóa toàn bộ vẫn cần xác nhận riêng; không có thay đổi nào ở tầng dữ liệu.
 */
@Composable
fun DataManagementDialog(
    onDismiss: () -> Unit,
    onExportJson: () -> Unit,
    onImportJson: () -> Unit = {},
    onLoadSampleData: () -> Unit,
    onClearAllData: () -> Unit
) {
    var confirmClear by remember { mutableStateOf(false) }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Color.White,
            title = { Text("Xóa toàn bộ bản ghi?", fontWeight = FontWeight.ExtraBold, color = WalletInk) },
            text = {
                Text(
                    "Xóa toàn bộ bản ghi giờ và nhật ký phản tư trên thiết bị này? Ngân sách đã đặt được giữ lại. Thao tác này không thể hoàn tác.",
                    fontSize = 12.sp,
                    color = WalletMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmClear = false
                        onDismiss()
                        onClearAllData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WalletDangerText)
                ) {
                    Text("Xóa toàn bộ", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Hủy", color = WalletMuted) }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("Dữ liệu & sao lưu", fontWeight = FontWeight.ExtraBold, color = WalletInk) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DataActionRow(title = "Xuất JSON", subtitle = "Lưu bản sao dữ liệu ra tệp") {
                    onDismiss()
                    onExportJson()
                }
                DataActionRow(title = "Nhập / khôi phục JSON", subtitle = "Gộp an toàn hoặc khôi phục từ tệp sao lưu") {
                    onDismiss()
                    onImportJson()
                }
                DataActionRow(title = "Nạp dữ liệu mẫu", subtitle = "Thêm dữ liệu thử để xem giao diện") {
                    onDismiss()
                    onLoadSampleData()
                }
                DataActionRow(
                    title = "Xóa toàn bộ bản ghi",
                    subtitle = "Giữ lại ngân sách · không thể hoàn tác",
                    danger = true
                ) { confirmClear = true }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Đóng", fontWeight = FontWeight.Bold) }
        }
    )
}

@Composable
private fun DataActionRow(title: String, subtitle: String, danger: Boolean = false, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (danger) WalletDangerBg else Color(0xFFF3F7F4))
            .walletClickable(onClickLabel = title, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (danger) WalletDangerText else WalletInk
        )
        Text(text = subtitle, fontSize = 12.sp, color = WalletMuted, modifier = Modifier.padding(top = 2.dp))
    }
}
