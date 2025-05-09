package com.hsl.product

import android.content.Context
import android.content.SharedPreferences
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * This activity contains various code issues for AI code review testing
 */
class ProblematicActivity : AppCompatActivity() {
    
    // 问题1: 静态Context引用可能导致内存泄漏
    companion object {
        private const val TAG = "ProblematicActivity"
        lateinit var staticContext: Context
        val executorService = Executors.newFixedThreadPool(10) // 线程池没有关闭机制
    }
    
    // 问题2: 未使用ViewModel，直接在Activity中管理数据
    private var counter = 0
    private var userData: String? = null
    
    // 问题3: 使用过时的AsyncTask
    private inner class FetchDataTask : AsyncTask<String, Void, String>() {
        override fun doInBackground(vararg params: String): String {
            // 问题4: 直接使用HttpURLConnection而不是推荐的网络库
            val url = URL(params[0])
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                response.append(line)
            }
            reader.close()
            
            return response.toString()
        }
        
        override fun onPostExecute(result: String) {
            // 问题5: 不检查Activity是否已销毁
            findViewById<TextView>(R.id.resultTextView).text = result
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_problematic)
        
        // 问题6: 存储静态Context引用
        staticContext = this
        
        // 问题7: 使用findViewById而不是ViewBinding
        val usernameInput = findViewById<EditText>(R.id.usernameInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val fetchDataButton = findViewById<Button>(R.id.fetchDataButton)
        
        // 问题8: 在UI线程中执行耗时操作
        loginButton.setOnClickListener {
            val username = usernameInput.text.toString()
            val password = passwordInput.text.toString()
            
            // 问题9: 明文存储敏感信息
            val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.putString("username", username)
            editor.putString("password", password) // 明文存储密码
            editor.apply()
            
            // 模拟网络请求
            Thread.sleep(2000) // 在UI线程中阻塞
            
            // 问题10: 直接使用Log记录敏感信息
            Log.d(TAG, "User logged in: $username, password: $password")
        }
        
        // 问题11: 使用GlobalScope而不是受控的作用域
        fetchDataButton.setOnClickListener {
            GlobalScope.launch {
                try {
                    // 问题12: 硬编码URL
                    val url = "http://example.com/api/data?user=admin&password=admin123"
                    
                    // 问题13: 不处理异常
                    val connection = URL(url).openConnection() as HttpURLConnection
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    
                    // 问题14: 在后台线程更新UI
                    findViewById<TextView>(R.id.resultTextView).text = response.toString()
                } catch (e: Exception) {
                    // 问题15: 空catch块
                }
            }
        }
        
        // 问题16: 使用过时的Handler
        Handler().postDelayed({
            // 问题17: 可能导致内存泄漏
            fetchData()
        }, 5000)
        
        // 问题18: SQL注入风险
        val searchButton = findViewById<Button>(R.id.searchButton)
        searchButton.setOnClickListener {
            val searchQuery = usernameInput.text.toString()
            // 问题19: 不验证用户输入
            val query = "SELECT * FROM users WHERE username LIKE '%$searchQuery%'"
            executeSQLQuery(query)
        }
    }
    
    private fun fetchData() {
        // 问题20: 执行网络请求但不检查网络状态
        FetchDataTask().execute("http://example.com/api/data")
    }
    
    private fun executeSQLQuery(query: String) {
        // 模拟SQL查询
        Log.d(TAG, "Executing SQL query: $query")
    }
    
    // 问题21: 资源泄漏
    override fun onDestroy() {
        super.onDestroy()
        // 没有关闭executorService
        // 没有取消正在进行的网络请求
    }
    
    // 问题22: 不安全的JSON解析
    private fun parseJson(jsonString: String): String {
        try {
            val jsonObject = JSONObject(jsonString)
            return jsonObject.getString("data")
        } catch (e: Exception) {
            // 问题23: 捕获所有异常但不处理
            return ""
        }
    }
    
    // 问题24: 内存泄漏的监听器
    private val networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) {
            super.onAvailable(network)
            // 问题25: 在回调中引用Activity
            Log.d(TAG, "Network available in ${this@ProblematicActivity}")
        }
    }
}
