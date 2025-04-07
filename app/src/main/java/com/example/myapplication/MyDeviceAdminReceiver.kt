import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    // Метод вызывается, когда права администратора активированы
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "Администратор устройства активирован", Toast.LENGTH_SHORT).show()
    }

    // Метод вызывается, когда права администратора деактивированы
    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "Администратор устройства деактивирован", Toast.LENGTH_SHORT).show()
    }

    // Метод для обработки потери пароля
    override fun onPasswordChanged(context: Context, intent: Intent) {
        super.onPasswordChanged(context, intent)
        // Здесь можно добавить действия, например, уведомление о смене пароля
    }

    // Метод для обработки событий при сбросе пароля
    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        // Например, можно логировать попытки входа
    }

    // Метод для получения состояния администратора устройства
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Дополнительная обработка интентов, например, для проверки состояния
    }
}
