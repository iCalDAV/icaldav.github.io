package com.icalendar.caldav

import com.icalendar.caldav.client.CalDavClient
import com.icalendar.caldav.discovery.CalDavDiscovery
import com.icalendar.webdav.model.DavResult
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.system.measureTimeMillis

class FullClientTest {
    
    @Test
    fun `test full fetchEvents`() {
        val username = "rockin.rav@icloud.com"
        val password = "uhqz-xvay-ylyc-lfxb"
        
        println("=== Full CalDavClient Test ===")
        
        // Step 1: Discover
        println("\n--- Step 1: Discover ---")
        val discovery = CalDavDiscovery.withBasicAuth(username, password)
        val discoverTime = measureTimeMillis {
            when (val result = discovery.discoverAccount("https://caldav.icloud.com")) {
                is DavResult.Success -> {
                    println("Discovery success")
                    println("Calendars: ${result.value.calendars.map { it.displayName }}")
                }
                else -> {
                    println("Discovery failed: $result")
                    return
                }
            }
        }
        println("Discover time: ${discoverTime}ms")
        
        // Step 2: Fetch events
        println("\n--- Step 2: Fetch Events ---")
        val client = CalDavClient.withBasicAuth(username, password)
        val calendarUrl = "https://p180-caldav.icloud.com:443/646691839/calendars/4D24D1CF-D573-4130-BFB7-F9E0B616E6FE/"
        
        val start = Instant.parse("2020-01-01T00:00:00Z")
        val end = Instant.parse("2030-12-31T23:59:59Z")
        
        println("Calling fetchEvents...")
        println("URL: $calendarUrl")
        println("Range: $start to $end")
        
        val fetchTime = measureTimeMillis {
            when (val result = client.fetchEvents(calendarUrl, start, end)) {
                is DavResult.Success -> {
                    println("Fetch success!")
                    println("Events: ${result.value.size}")
                }
                is DavResult.HttpError -> println("HTTP Error: ${result.code} ${result.message}")
                is DavResult.NetworkError -> println("Network Error: ${result.exception.message}")
                is DavResult.ParseError -> println("Parse Error: ${result.message}")
            }
        }
        println("Fetch time: ${fetchTime}ms")
        
        println("\n--- Total ---")
        println("Total time: ${discoverTime + fetchTime}ms")
    }
}
