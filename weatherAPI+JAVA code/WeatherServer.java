import java.io.*;
import java.net.*;
import com.sun.net.httpserver.*;

public class WeatherServer {

    private static final int PORT = 8000;

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/weather", WeatherServer::handleRequest);
        server.setExecutor(null);
        System.out.println("✅ Server running at http://localhost:" + PORT);
        server.start();
    }

    private static void handleRequest(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            exchange.sendResponseHeaders(405, -1); // Method Not Allowed
            return;
        }

        String query = exchange.getRequestURI().getQuery(); // e.g., q=London
        if (query == null || !query.startsWith("q=")) {
            exchange.sendResponseHeaders(400, -1); // Bad Request
            return;
        }

        String city = URLDecoder.decode(query.substring(2), "UTF-8");
        String apiKey = loadApiKey();
        if (apiKey.isEmpty()) {
            System.out.println("❌ API key is missing or invalid.");
            exchange.sendResponseHeaders(500, -1); // Internal Server Error
            return;
        }

        String apiUrl = "https://api.weatherapi.com/v1/current.json?key=" + apiKey + "&q=" + URLEncoder.encode(city, "UTF-8") + "&aqi=no";

        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setRequestMethod("GET");

            int responseCode = conn.getResponseCode();
            InputStream is = (responseCode == 200) ? conn.getInputStream() : conn.getErrorStream();

            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
            reader.close();

            byte[] response = json.toString().getBytes("UTF-8");
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseCode, response.length);

            OutputStream os = exchange.getResponseBody();
            os.write(response);
            os.close();

        } catch (IOException e) {
            e.printStackTrace();
            exchange.sendResponseHeaders(502, -1); // Bad Gateway
        }
    }

    private static String loadApiKey() {
        try (BufferedReader br = new BufferedReader(new FileReader("wapi.txt"))) {
            String key = br.readLine();
            if (key == null || key.trim().isEmpty()) {
                throw new IOException("API key is empty");
            }
            return key.trim().replaceAll("[^a-zA-Z0-9]", "");
        } catch (IOException e) {
            System.err.println("❌ Failed to load API key from wapi.txt: " + e.getMessage());
            return "";
        }
    }
}
