package com.rotemyanco.torim.services

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.http.*


object KtorClientManager {

	fun getClient(): HttpClient {
		return HttpClient(CIO) {
			install(ContentNegotiation)
			install(HttpCookies) {

				storage = ClientCookiesStorage(AcceptAllCookiesStorage())
			}
		}
	}
}

class ClientCookiesStorage(private val cookiesStorage: CookiesStorage) : CookiesStorage by cookiesStorage {
	override suspend fun get(requestUrl: Url): List<Cookie> = cookiesStorage.get(requestUrl).map { it.copy(encoding = CookieEncoding.URI_ENCODING) }
}
