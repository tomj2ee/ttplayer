package org.ttplayer.lyrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class LyricSearchService {

    private static final String BASE_URL = "https://music.gd.cn";
    private static final String SEARCH_API = BASE_URL + "/api/lyrics/search";
    private static final String LYRIC_API = BASE_URL + "/api/lyrics/lyric";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final String REFERER = "https://music.gd.cn/";

    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 搜索歌曲。
     *
     * @param keyword  搜索关键词（歌名/歌手）
     * @param pageSize 单页数量
     */
    public static List<SongResult> searchSong(String keyword, int pageSize) throws IOException {
        HttpUrl url = HttpUrl.parse(SEARCH_API).newBuilder()
            .addQueryParameter("keyword", keyword)
            .addQueryParameter("pageSize", String.valueOf(pageSize))
            .build();

        System.out.println("[LyricSearchService] searchSong keyword=" + keyword
            + " pageSize=" + pageSize + " url=" + url);

        String json = httpGet(url.toString());
        System.out.println("[LyricSearchService] searchSong response=" + truncate(json, 500));
        return parseSearchResults(json);
    }

    /**
     * 根据 mid 获取歌词（LRC 文本）。
     */
    public static String getLyricByMid(String mid) throws IOException {
        HttpUrl url = HttpUrl.parse(LYRIC_API).newBuilder()
            .addQueryParameter("mid", mid)
            .build();

        System.out.println("[LyricSearchService] getLyricByMid mid=" + mid + " url=" + url);

        String json = httpGet(url.toString());
        System.out.println("[LyricSearchService] getLyricByMid response=" + truncate(json, 500));
        return parseLyricResponse(json);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max) + "...(" + s.length() + ")";
    }

    private static String httpGet(String urlStr) throws IOException {
        Request request = new Request.Builder()
            .url(urlStr)
            .header("User-Agent", USER_AGENT)
            .header("Referer", REFERER)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            return response.body().string();
        }
    }

    private static List<SongResult> parseSearchResults(String json) throws IOException {
        List<SongResult> results = new ArrayList<>();
        JsonNode root = objectMapper.readTree(json);
        if (!root.path("success").asBoolean(false)) {
            return results;
        }
        JsonNode itemsNode = root.path("data");
        if (!itemsNode.isArray()) {
            return results;
        }
        for (JsonNode item : itemsNode) {
            String mid = item.path("mid").asText("");
            String name = item.path("name").asText("");
            String singer = item.path("singer").asText("");
            String album = item.path("album").asText("");
            results.add(new SongResult(mid, name, singer, album));
        }
        return results;
    }

    /**
     * 后端返回的歌词已是解码后的明文 LRC。
     */
    private static String parseLyricResponse(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        if (!root.path("success").asBoolean(false)) {
            return "";
        }
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull() || !data.isTextual()) {
            return "";
        }
        return data.asText();
    }
}