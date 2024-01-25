import groovy.json.JsonSlurper
import groovyx.net.http.HttpResponseException
import groovyx.net.http.HttpBuilder

def http = new HttpBuilder('https://example.com')

try {
    def response = http.post(path: '/data') {
requestContentType = 'application/json'
            body = [param1: 'value1', param2: 'value2']
            response.success = { resp, json ->
                println "Response status: ${resp.statusLine}"
                println "Response data: ${json}"
            }
}
} catch (HttpResponseException e) {
    println "Error status: ${e.statusCode}"
    println "Error message: ${e.message}"
}
