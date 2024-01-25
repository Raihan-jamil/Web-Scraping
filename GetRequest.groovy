import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.methods.GetMethod

def client = new HttpClient()
def method = new GetMethod('http://example.com')

try {
    client.executeMethod(method)
    println method.getResponseBodyAsString()
} finally {
    method.releaseConnection()
}
