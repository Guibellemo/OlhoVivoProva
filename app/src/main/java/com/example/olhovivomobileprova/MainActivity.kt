package com.example.olhovivomobileprova

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONArray
import org.json.JSONException
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy

class MainActivity : AppCompatActivity() {

    private lateinit var editTextLinha: EditText
    private lateinit var editTextSentido: EditText
    private lateinit var buttonBuscar: Button
    private lateinit var textViewNumeroLinha: TextView
    private lateinit var textViewSentidoPrincipal: TextView
    private lateinit var textViewSentidoSecundario: TextView

    private lateinit var requestQueue: RequestQueue
    private var sessionCookie: String? = null

    companion object {
        private const val BASE_URL = "https://api.olhovivo.sptrans.com.br/v2.1"
        private const val TOKEN = "870af6db539203f2a31eb10bcf64e50edf82dce4fa212f6ef3e5d640185ed6bd"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Configurar gerenciamento de cookies
        CookieHandler.setDefault(CookieManager(null, CookiePolicy.ACCEPT_ALL))

        // Inicializar componentes
        editTextLinha = findViewById(R.id.editTextLinha)
        editTextSentido = findViewById(R.id.editTextSentido)
        buttonBuscar = findViewById(R.id.buttonBuscar)
        textViewNumeroLinha = findViewById(R.id.textViewNumeroLinha)
        textViewSentidoPrincipal = findViewById(R.id.textViewSentidoPrincipal)
        textViewSentidoSecundario = findViewById(R.id.textViewSentidoSecundario)

        // Inicializar RequestQueue
        requestQueue = Volley.newRequestQueue(this)

        // Configurar listener do botão
        buttonBuscar.setOnClickListener {
            buscarLinha()
        }
    }

    private fun buscarLinha() {
        val termosBusca = editTextLinha.text.toString().trim()
        val sentido = editTextSentido.text.toString().trim()

        if (termosBusca.isEmpty()) {
            Toast.makeText(this, "Digite o número ou nome da linha", Toast.LENGTH_SHORT).show()
            return
        }

        if (sentido.isEmpty()) {
            Toast.makeText(this, "Digite o sentido (1 ou 2)", Toast.LENGTH_SHORT).show()
            return
        }

        // Limpar resultados anteriores
        limparResultados()

        // Primeiro, autenticar com o token
        autenticarToken { sucesso ->
            if (sucesso) {
                // Se autenticação foi bem-sucedida, fazer a busca da linha
                buscarLinhaSentido(termosBusca, sentido)
            } else {
                Toast.makeText(this, "Erro na autenticação. Verifique o token.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun autenticarToken(callback: (Boolean) -> Unit) {
        val url = "$BASE_URL/Login/Autenticar?token=$TOKEN"

        val stringRequest = object : StringRequest(
            Method.POST,
            url,
            { response ->
                val sucesso = response.trim().equals("true", ignoreCase = true)
                if (sucesso) {
                    Toast.makeText(this, "Autenticação realizada com sucesso!", Toast.LENGTH_SHORT).show()
                }
                callback(sucesso)
            },
            { error ->
                error.printStackTrace()

                // Log mais detalhado do erro
                val errorMsg = when {
                    error.networkResponse != null -> {
                        "Código: ${error.networkResponse.statusCode}, Dados: ${String(error.networkResponse.data)}"
                    }
                    error.message != null -> error.message!!
                    else -> "Erro desconhecido na autenticação"
                }

                Toast.makeText(this, "Erro na autenticação: $errorMsg", Toast.LENGTH_LONG).show()
                callback(false)
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                // Remover Content-Type para requisição POST simples
                // headers["Content-Type"] = "application/json"
                return headers
            }

            override fun parseNetworkResponse(response: com.android.volley.NetworkResponse?): com.android.volley.Response<String> {
                // Capturar cookies da resposta
                response?.headers?.get("Set-Cookie")?.let { cookie ->
                    sessionCookie = cookie
                }
                return super.parseNetworkResponse(response)
            }
        }

        requestQueue.add(stringRequest)
    }

    private fun buscarLinhaSentido(termosBusca: String, sentido: String) {
        val url = "$BASE_URL/Linha/BuscarLinhaSentido?termosBusca=$termosBusca&sentido=$sentido"

        val jsonArrayRequest = object : JsonArrayRequest(
            Request.Method.GET,
            url,
            null,
            { response ->
                try {
                    if (response.length() > 0) {
                        // Pegar primeiro resultado
                        val linha = response.getJSONObject(0)

                        // Extrair dados
                        val numeroLinha = linha.getString("lt")
                        val sentidoPrincipal = linha.getString("tp")
                        val sentidoSecundario = linha.getString("ts")

                        // Exibir resultados
                        exibirResultados(numeroLinha, sentidoPrincipal, sentidoSecundario)

                    } else {
                        Toast.makeText(this, "Nenhuma linha encontrada", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                    Toast.makeText(this, "Erro ao processar dados: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                error.printStackTrace()

                val errorMsg = when {
                    error.networkResponse != null -> {
                        "Código: ${error.networkResponse.statusCode}"
                    }
                    error.message != null -> error.message!!
                    else -> "Erro desconhecido na busca"
                }

                Toast.makeText(this, "Erro na requisição: $errorMsg", Toast.LENGTH_LONG).show()
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                // Incluir cookie de sessão se disponível
                sessionCookie?.let { cookie ->
                    headers["Cookie"] = cookie
                }
                return headers
            }
        }

        requestQueue.add(jsonArrayRequest)
    }

    private fun exibirResultados(numeroLinha: String, sentidoPrincipal: String, sentidoSecundario: String) {
        textViewNumeroLinha.text = "Linha: $numeroLinha"
        textViewSentidoPrincipal.text = "Terminal Principal → Secundário:\n$sentidoPrincipal"
        textViewSentidoSecundario.text = "Terminal Secundário → Principal:\n$sentidoSecundario"

        // Tornar visíveis os campos de resultado
        textViewNumeroLinha.visibility = View.VISIBLE
        textViewSentidoPrincipal.visibility = View.VISIBLE
        textViewSentidoSecundario.visibility = View.VISIBLE
    }

    private fun limparResultados() {
        textViewNumeroLinha.visibility = View.GONE
        textViewSentidoPrincipal.visibility = View.GONE
        textViewSentidoSecundario.visibility = View.GONE
    }
}