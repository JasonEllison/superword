/**
 *
 * APDPlat - Application Product Development Platform Copyright (c) 2013, 杨尚川,
 * yang-shangchuan@qq.com
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.apdplat.superword.tools;

import org.apache.commons.lang.StringUtils;
import org.apdplat.superword.model.Word;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 利用爱词霸筛选词表中属于各大考试的词
 * @author 杨尚川
 */
public class WordClassifier {
    private WordClassifier(){}

    private static final Logger LOGGER = LoggerFactory.getLogger(WordClassifier.class);
    private static final String ICIBA = "http://www.iciba.com/";
    private static final String TYPE_CSS_PATH = "html body.bg_main div#layout div#center div#main_box div#dict_main div.dictbar div.wd_genre a";
    private static final String UNFOUND_CSS_PATH = "html body.bg_main div#layout div#center div#main_box div#dict_main div#question.question.unfound_tips";
    private static final String ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
    private static final String ENCODING = "gzip, deflate";
    private static final String LANGUAGE = "zh-cn,zh;q=0.8,en-us;q=0.5,en;q=0.3";
    private static final String CONNECTION = "keep-alive";
    private static final String HOST = "www.iciba.com";
    private static final String REFERER = "http://www.iciba.com/";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.10; rv:36.0) Gecko/20100101 Firefox/36.0";
    private static final Set<String> NOT_FOUND_WORDS = new HashSet<>();
    private static final Set<String> ORIGIN_HTML = new HashSet<>();

    public static void classify(Set<Word> words){
        LOGGER.debug("待处理词数目："+words.size());
        AtomicInteger i = new AtomicInteger();
        Map<String, List<String>> data = new HashMap<>();
        words.forEach(word -> {
            if(i.get()%1000 == 999){
                save(data);
            }
            showStatus(data, i.incrementAndGet(), words.size(), word.getWord());
            String html = getContent(word.getWord());
            //LOGGER.debug("获取到的HTML：" +html);
            while(html.contains("非常抱歉，来自您ip的请求异常频繁")){
                //使用新的IP地址
                DynamicIp.toNewIp();
                html = getContent(word.getWord());
            }
            if(StringUtils.isNotBlank(html)) {
                parse(word.getWord(), html, data);
                if(!NOT_FOUND_WORDS.contains(word.getWord())) {
                    ORIGIN_HTML.add(word.getWord() + "杨尚川" + html);
                }
            }else{
                NOT_FOUND_WORDS.add(word.getWord());
            }

        });
        //写入磁盘
        save(data);
        LOGGER.debug("处理完毕，总词数目："+words.size());
    }
    public static void parse(String file){
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new BufferedInputStream(
                                new FileInputStream(file))))) {
            Map<String, List<String>> data = new HashMap<>();
            String line = null;
            while ((line = reader.readLine()) != null) {
                parse(line, data);
            }
            save(data);
        } catch (IOException e) {
            LOGGER.error("解析文本出错", e);
        }
    }
    public static void parse(String html, Map<String, List<String>> data){
        String[] attr = html.split("杨尚川");
        if(attr == null || attr.length != 2){
            LOGGER.error("解析文本失败，文本应该以'杨尚川'分割，前面是词，后面是网页，网页内容是去除换行符之后的一整行文本："+html);
            return;
        }
        String word = attr[0];
        LOGGER.info("解析单词："+word);
        String htm = attr[1];
        parse(word, htm, data);
    }

    public static void showStatus(Map<String, List<String>> data, int current, int total, String word){
        LOGGER.debug("开始处理词 "+current+"/"+total+" ，完成进度 "+current/(float)total*100+"% ："+word);
        data.entrySet().forEach(e -> {
            LOGGER.debug(e.getKey()+"\t"+e.getValue().size());
        });
    }

    public static void save(Map<String, List<String>> data){
            LOGGER.info("将数据写入磁盘，防止丢失");
            data.keySet().forEach(key -> {
                try {
                    String path = "src/main/resources/word_" + key + ".txt";
                    LOGGER.error("保存词典文件：" + path);
                    List<String> existWords = Files.readAllLines(Paths.get(path));
                    Set<String> allWords = new HashSet<>();
                    existWords.forEach(line -> {
                        String[] attr = line.split("\\s+");
                        if(attr != null) {
                            String w = "";
                            if(attr.length == 1){
                                w = attr[0];
                            }
                            if(attr.length == 2){
                                w = attr[1];
                            }
                            allWords.add(w);
                        }
                    });
                    allWords.addAll(data.get(key));
                    AtomicInteger i = new AtomicInteger();
                    List<String> list = allWords
                            .stream()
                            .sorted()
                            .map(word -> i.incrementAndGet()+"\t" + word)
                            .collect(Collectors.toList());
                    Files.write(Paths.get(path), list);
                    data.get(key).clear();
                    existWords.clear();
                    allWords.clear();
                    list.clear();
                }catch (Exception e){
                    LOGGER.error("保存词典文件失败", e);
                }
            });
        data.clear();
        try {
            if(!NOT_FOUND_WORDS.isEmpty()) {
                String path = "src/main/resources/word_not_found.txt";
                LOGGER.error("保存词典文件：" + path);
                AtomicInteger i = new AtomicInteger();
                //NOT_FOUND_WORDS比较少，常驻内存
                List<String> list = NOT_FOUND_WORDS
                        .stream()
                        .sorted()
                        .map(word -> i.incrementAndGet() + "\t" + word)
                        .collect(Collectors.toList());
                Files.write(Paths.get(path), list);
                list.clear();
            }
            //保存原始HTML
            if(!ORIGIN_HTML.isEmpty()) {
                String path = "src/main/resources/origin_html_" + System.currentTimeMillis() + ".txt";
                LOGGER.error("保存词典文件：" + path);
                Files.write(Paths.get(path), ORIGIN_HTML);
                ORIGIN_HTML.clear();
            }
        }catch (Exception e){
            LOGGER.error("保存词典文件失败", e);
        }
    }

    public static String getContent(String word) {
        String url = ICIBA + word + "?renovate=" + (new Random(System.currentTimeMillis()).nextInt(899999)+100000);
        LOGGER.debug("url:"+url);
        Connection conn = Jsoup.connect(url)
                .header("Accept", ACCEPT)
                .header("Accept-Encoding", ENCODING)
                .header("Accept-Language", LANGUAGE)
                .header("Connection", CONNECTION)
                .header("Referer", REFERER)
                .header("Host", HOST)
                .header("User-Agent", USER_AGENT)
                .ignoreContentType(true);
        String html = "";
        try {
            html = conn.post().html();
            html = html.replaceAll("[\n\r]", "");
        }catch (Exception e){
            LOGGER.error("获取URL："+url+"页面出错", e);
        }
        return html;
    }

    public static void parse(String word, String html, Map<String, List<String>> data){
        Document doc = Jsoup.parse(html);
        Elements es = doc.select(TYPE_CSS_PATH);
        for(Element e : es){
            String type = e.text();
            LOGGER.debug("获取到的类型："+type);
            if(StringUtils.isNotBlank(type)){
                data.putIfAbsent(type, new ArrayList<>());
                data.get(type).add(word);
            }
        }
        es = doc.select(UNFOUND_CSS_PATH);
        for(Element e : es){
            String notFound = e.text();
            LOGGER.debug("没有该词："+notFound);
            if(StringUtils.isNotBlank(notFound)
                    && (notFound.contains("对不起，没有找到")
                        || notFound.contains("您要查找的是不是"))){
                NOT_FOUND_WORDS.add(word);
            }
        }
    }

    public static void main(String[] args) {
        //Set<Word> words = new HashSet<>();
        //words.add(new Word("time", ""));
        //words.add(new Word("yangshangchuan", ""));
        //classify(words);
        //parse("src/main/resources/origin_html_1427060576977.txt");
        classify(WordSources.getAll());
    }
}
