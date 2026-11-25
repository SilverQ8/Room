package com.roomplanner.service;

import com.roomplanner.model.Furniture;
import com.roomplanner.model.ShoppingProduct;

import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 네이버 쇼핑 검색 API 연동 서비스.
 * API 키가 등록되지 않았을 때는 현실적인 가구 샘플 데이터를 반환하여 테스트를 지원한다.
 */
public class NaverShoppingService {

    private static final String API_URL = "https://openapi.naver.com/v1/search/shop.json";
    private static final Path CONFIG_PATH = Paths.get("config", "naver_api.properties");

    private String clientId = "";
    private String clientSecret = "";
    private final HttpClient httpClient;

    public NaverShoppingService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();
        loadApiConfig();
    }

    public synchronized void setApiCredentials(String clientId, String clientSecret) {
        this.clientId = (clientId != null) ? clientId.trim() : "";
        this.clientSecret = (clientSecret != null) ? clientSecret.trim() : "";
        saveApiConfig();
    }

    public synchronized String getClientId() { return clientId; }
    public synchronized String getClientSecret() { return clientSecret; }
    public synchronized boolean hasApiCredentials() {
        return !clientId.isEmpty() && !clientSecret.isEmpty();
    }

    private void loadApiConfig() {
        if (!Files.exists(CONFIG_PATH)) return;
        try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
            Properties prop = new Properties();
            prop.load(in);
            this.clientId = prop.getProperty("client.id", "").trim();
            this.clientSecret = prop.getProperty("client.secret", "").trim();
        } catch (IOException ignored) {}
    }

    private void saveApiConfig() {
        try {
            if (!Files.exists(CONFIG_PATH.getParent())) {
                Files.createDirectories(CONFIG_PATH.getParent());
            }
            Properties prop = new Properties();
            prop.setProperty("client.id", clientId);
            prop.setProperty("client.secret", clientSecret);
            try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
                prop.store(out, "Naver Search API Configuration");
            }
        } catch (IOException ignored) {}
    }

    /**
     * 네이버 쇼핑 검색을 수행하고, 목표 치수 기준 근사치 순으로 정렬하여 반환한다.
     */
    public List<ShoppingProduct> searchAndRecommend(Furniture.Type type, String customKeyword,
                                                    double targetWidthMm, double targetDepthMm, double targetHeightMm) throws Exception {
        String query = buildSearchQuery(type, customKeyword, targetWidthMm);

        List<ShoppingProduct> items;
        if (hasApiCredentials()) {
            items = callNaverApi(query);
        } else {
            // API 키가 없으면 체험용 모의(Mock) 데이터 제공
            items = getMockProducts(type, targetWidthMm, targetDepthMm);
        }

        // 각 상품의 치수 파싱 및 근사치 점수 계산
        for (ShoppingProduct p : items) {
            ProductDimensionParser.parseAndScore(p, type, targetWidthMm, targetDepthMm, targetHeightMm);
        }

        // 근사치 오차(오름차순) 정렬
        items.sort(Comparator.comparingDouble(ShoppingProduct::getDistanceScore));

        return items;
    }

    private String buildSearchQuery(Furniture.Type type, String customKeyword, double targetWidthMm) {
        StringBuilder sb = new StringBuilder();
        String baseType = switch (type) {
            case BED -> "침대";
            case DESK -> "책상";
            case CHAIR -> "의자";
            case WARDROBE -> "옷장";
            case SOFA -> "소파";
            case TV_STAND -> "거실장 TV장";
        };
        sb.append(baseType);

        if (customKeyword != null && !customKeyword.trim().isEmpty()) {
            sb.append(" ").append(customKeyword.trim());
        }

        // 폭 수치를 쿼리에 힌트로 추가 (예: "1200", "1600")
        if (targetWidthMm >= 600 && targetWidthMm <= 2400) {
            int roundW = (int) (Math.round(targetWidthMm / 100.0) * 100);
            sb.append(" ").append(roundW);
        }

        return sb.toString();
    }

    private List<ShoppingProduct> callNaverApi(String query) throws Exception {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = API_URL + "?query=" + encoded + "&display=30&sort=sim";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-Naver-Client-Id", clientId)
                .header("X-Naver-Client-Secret", clientSecret)
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new IOException("네이버 API 호출 실패 (HTTP " + resp.statusCode() + "): " + resp.body());
        }

        return parseJsonItems(resp.body());
    }

    /** 외부 라이브러리 없이 네이버 쇼핑 JSON 응답을 빠르고 안전하게 파싱 */
    private List<ShoppingProduct> parseJsonItems(String json) {
        List<ShoppingProduct> list = new ArrayList<>();

        // items 배열 내부 추출
        int itemsIdx = json.indexOf("\"items\":");
        if (itemsIdx < 0) return list;
        String itemsBlock = json.substring(itemsIdx);

        // 개별 JSON 객체 추출 정규식
        Pattern objPattern = Pattern.compile("\\{[^{}]+?\\}");
        Matcher m = objPattern.matcher(itemsBlock);

        while (m.find()) {
            String obj = m.group();
            String title = extractJsonField(obj, "title");
            String link = extractJsonField(obj, "link");
            String image = extractJsonField(obj, "image");
            String lpriceStr = extractJsonField(obj, "lprice");
            String mallName = extractJsonField(obj, "mallName");
            String brand = extractJsonField(obj, "brand");

            int price = 0;
            try { price = Integer.parseInt(lpriceStr); } catch (Exception ignored) {}

            if (title != null && !title.isEmpty()) {
                list.add(new ShoppingProduct(title, link, image, price, mallName, brand));
            }
        }
        return list;
    }

    private String extractJsonField(String json, String field) {
        Pattern p = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1).replace("\\/", "/").replace("\\\"", "\"");
        }
        return "";
    }

    /** API 키가 등록되지 않았을 때 동작할 샘플 데이터 */
    private List<ShoppingProduct> getMockProducts(Furniture.Type type, double targetW, double targetD) {
        List<ShoppingProduct> list = new ArrayList<>();
        int tw = (int) (targetW > 0 ? targetW : type.width);
        int td = (int) (targetD > 0 ? targetD : type.depth);

        switch (type) {
            case DESK -> {
                list.add(new ShoppingProduct("[체험용] 원목 컴퓨터 책상 " + tw + "x" + td + " 모던 1인용",
                        "https://search.shopping.naver.com", "", 89000, "가구몰", "홈스타일"));
                list.add(new ShoppingProduct("[체험용] 멀티 철제 프레임 책상 " + (tw + 100) + "x" + td,
                        "https://search.shopping.naver.com", "", 115000, "데스크존", "어반"));
                list.add(new ShoppingProduct("[체험용] 슬림 미니멀 책상 " + (tw - 100) + "x" + (td - 50) + " 화이트",
                        "https://search.shopping.naver.com", "", 68000, "리빙스토어", "베이직"));
                list.add(new ShoppingProduct("[체험용] 하이엔드 게이밍 데스크 " + (tw + 200) + "x" + (td + 100),
                        "https://search.shopping.naver.com", "", 189000, "게이밍기어", "에이펙스"));
            }
            case BED -> {
                list.add(new ShoppingProduct("[체험용] 호텔식 원목 침대 프레임 퀸 Q 1500x2000",
                        "https://search.shopping.naver.com", "", 329000, "슬립웨어", "포레스트"));
                list.add(new ShoppingProduct("[체험용] 수납형 슈퍼싱글 침대 SS 1100x2000 매트리스 포함",
                        "https://search.shopping.naver.com", "", 249000, "룸앤코", "편안한밤"));
                list.add(new ShoppingProduct("[체험용] 프리미엄 패브릭 헤드 침대 킹 K 1600x2000",
                        "https://search.shopping.naver.com", "", 489000, "더베드", "시그니처"));
            }
            case SOFA -> {
                list.add(new ShoppingProduct("[체험용] 아쿠아텍스 패브릭 3인용 소파 1800x850 방수",
                        "https://search.shopping.naver.com", "", 340000, "소파앤샵", "코지홈"));
                list.add(new ShoppingProduct("[체험용] 천연 가죽 4인용 카우치 소파 2400x950",
                        "https://search.shopping.naver.com", "", 790000, "리빙아트", "마스터"));
            }
            default -> {
                list.add(new ShoppingProduct("[체험용] " + type.label + " 맞춤 규격 " + tw + "x" + td,
                        "https://search.shopping.naver.com", "", 120000, "인테리어샵", "스탠다드"));
            }
        }
        return list;
    }
}
