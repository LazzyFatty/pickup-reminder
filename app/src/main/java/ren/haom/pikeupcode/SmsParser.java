package ren.haom.pikeupcode;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure Java: all recognition runs locally; unknown optional fields stay empty. */
public final class SmsParser {
    private static final String TOKEN = "[A-Za-z0-9]+(?:[\\s]*[-—–－_][\\s]*[A-Za-z0-9]+)*";
    private static final String LABEL = "(?:取件码|提货码|取货码|领取码|取包码|取件密码|取货密码|提货密码|开柜码|取件口令|提货编号|取件编号|提货号)";
    private static final String GAP = "[\\s:：为是码号【\\[（(‘’“”\"']*";
    private static final Pattern EXPLICIT = Pattern.compile(LABEL + GAP + "(" + TOKEN + ")", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMPLICIT = Pattern.compile("(?:请)?凭" + GAP + "(" + TOKEN + ")[】\\]）)‘’“”\"']*\\s*(?:到|至|在|前往|取件|取货|取快递|取包裹|领取)");
    private static final Pattern MORE_CODE = Pattern.compile("^[\\s】\\]）)‘’“”\"']*[,，、/及和]\\s*(" + TOKEN + ")");
    private static final Pattern LOGISTICS = Pattern.compile("快递|包裹|快件|驿站|取件|提货|取货|自提|开柜");
    private static final Pattern URL = Pattern.compile("(?i)(?:https?://|www\\.)[A-Za-z0-9][A-Za-z0-9./?=&%#_~+;:@!$()*,-]*");
    private static final Pattern COURIER = Pattern.compile("顺丰(?:速运|快递)?|中通(?:快递)?|圆通(?:速递|快递)?|申通(?:快递)?|韵达(?:快递|速递)?|极兔(?:速递|快递)?|京东(?:物流|快递)?|邮政(?:快递|速递)?|EMS|德邦(?:快递|物流)?|百世(?:快递)?|天天快递|优速快递|跨越速运|菜鸟速递|丹鸟(?:快递)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRACKING = Pattern.compile("(?:物流单号|快递单号|运单号|运单|单号|快递)(?:尾号)?\\s*[:：为是]*\\s*([*＊]?[A-Za-z0-9]{5,30})(?![A-Za-z0-9])", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAIL = Pattern.compile("(?:运单|快递|包裹|单号)?尾号(?:为|是)?\\s*[:：]?\\s*([*＊]?[A-Za-z0-9]{3,12})");
    private static final Pattern PHONE = Pattern.compile("(?<![A-Za-z0-9])(?:1[3-9][0-9]{9}|0[0-9]{2,3}[- ]?[0-9]{7,8}|400[- ]?[0-9]{3}[- ]?[0-9]{4}|95[0-9]{3,6})(?![A-Za-z0-9])");
    private static final String STOP = "(?=取件|取货|提货|领取|取包裹|取快递|取运单|取尾号|拿快递|[，,。;；!！\\n]|$)";
    private static final Pattern ADDRESS_LABEL = Pattern.compile("(?:驿站地址|取件地址|自提地址|地址)\\s*[:：为是]?\\s*([^，,。;；!！\\n]{2,100}?)" + STOP);
    private static final Pattern DESTINATION = Pattern.compile("(?:已(?:送至|送到|到达|到|放在|放至|放入|存放于|存放在)|请(?:您)?(?:前往|到)|到|前往|至)\\s*([^，,。;；!！\\n]{2,100}?)" + STOP);
    private static final Pattern STATION = Pattern.compile("([^,，。;；!！:：\\n【】]{1,35}?(?:快递驿站|驿站|快递服务站|快递站|快递超市|自提点|代收点|快递柜|收发室|服务中心))");
    private static final Pattern PLACE_PAIR = Pattern.compile("(?m)^[\\t ]*([^|\\r\\n]{2,40})\\|([^|\\r\\n]{2,100}?)[\\t \\\\]*\\r?$");
    private static final Pattern PLACE_INSTRUCTION = Pattern.compile("请|联系|咨询|电话|客服|验证码|运单|尾号|取件码|提货码|https?", Pattern.CASE_INSENSITIVE);

    private SmsParser() {}

    public static List<Parcel> parse(String body) {
        List<Parcel> result = new ArrayList<>();
        if (body == null || body.isEmpty() || body.length() > 20000) return result;
        String text = Normalizer.normalize(body, Normalizer.Form.NFKC).replace('\u00a0', ' ');
        if (!LOGISTICS.matcher(text).find()) return result;
        Set<String> codes = new LinkedHashSet<>();
        collect(EXPLICIT.matcher(text), text, codes);
        if (codes.isEmpty()) collect(IMPLICIT.matcher(text), text, codes);
        if (codes.isEmpty()) return result;

        String url = find(URL, text, 0).replaceAll("[.,;!?)]+$", "");
        String withoutUrl = URL.matcher(text).replaceAll(" ");
        String company = find(COURIER, withoutUrl, 0);
        String tracking = find(TRACKING, withoutUrl, 1);
        boolean tail = false;
        Matcher trackingMatcher = TRACKING.matcher(withoutUrl);
        if (trackingMatcher.find()) tail = trackingMatcher.group().contains("尾号") || tracking.startsWith("*");
        if (tracking.isEmpty()) { tracking = find(TAIL, withoutUrl, 1); tail = !tracking.isEmpty(); }
        if (tail) tracking = "尾号 " + tracking.replace("*", "");
        String address = cleanPlace(find(ADDRESS_LABEL, withoutUrl, 1));
        if (address.isEmpty()) {
            Matcher destinations = DESTINATION.matcher(withoutUrl);
            while (destinations.find()) {
                String candidate = cleanPlace(destinations.group(1));
                if (plausiblePlace(candidate)) { address = candidate; break; }
            }
        }
        String station = find(STATION, address, 1).replaceAll("^.*(?:号楼|号|栋|座|室)", "");
        if (station.isEmpty()) {
            Matcher header = Pattern.compile("^[\\s]*【([^】]{1,35})】").matcher(text);
            if (header.find() && STATION.matcher(header.group(1)).matches()) station = header.group(1);
        }
        if (station.isEmpty()) {
            String labeled = find(Pattern.compile("(?:驿站名称|站点名称|取件点)\\s*[:：]\\s*([^，,。;；\\n]{2,40})"), text, 1);
            station = cleanPlace(labeled);
        }
        if (station.isEmpty() || address.isEmpty()) {
            Matcher pairs = PLACE_PAIR.matcher(withoutUrl);
            while (pairs.find()) {
                String name = cleanPlace(pairs.group(1));
                String location = cleanPlace(pairs.group(2));
                if (STATION.matcher(name).matches() && plausiblePlace(name) && plausiblePlace(location)
                        && !PLACE_INSTRUCTION.matcher(name + location).find()) {
                    if (station.isEmpty()) station = name;
                    if (address.isEmpty()) address = location;
                    break;
                }
            }
        }
        String phone = "";
        Matcher phones = PHONE.matcher(withoutUrl);
        while (phones.find()) {
            String number = phones.group();
            if (!codes.contains(number) && !tracking.contains(number)) { phone = number; break; }
        }
        for (String code : codes) {
            Parcel p = new Parcel();
            p.code = code; p.company = company; p.station = station; p.address = address;
            p.tracking = tracking; p.phone = phone; p.url = url; p.body = body;
            result.add(p);
        }
        return result;
    }

    private static void collect(Matcher matcher, String text, Set<String> codes) {
        while (matcher.find()) {
            addCode(matcher.group(1), codes);
            String rest = text.substring(matcher.end());
            Matcher more = MORE_CODE.matcher(rest);
            while (more.find()) {
                if (!validCode(more.group(1))) break;
                addCode(more.group(1), codes);
                rest = rest.substring(more.end());
                more = MORE_CODE.matcher(rest);
            }
        }
    }

    private static void addCode(String raw, Set<String> codes) {
        if (validCode(raw)) codes.add(raw.replaceAll("\\s", "").replaceAll("[—–－_]", "-").toUpperCase(Locale.ROOT));
    }

    private static boolean validCode(String raw) {
        String code = raw.replaceAll("\\s", "");
        int length = code.replaceAll("[-—–－_]", "").length();
        // shortcut: conservative 3-12 character codes; widen only with real SMS examples and negative tests.
        return length >= 3 && length <= 12 && code.matches(".*[0-9].*")
                && !code.matches("1[3-9][0-9]{9}|400[0-9]{7}|95[0-9]{3,6}");
    }

    private static boolean plausiblePlace(String place) {
        return !place.isEmpty() && !place.matches("站|驿站|达|货|件|包裹|快递|家门口")
                && Pattern.compile("路|街|巷|号|区|村|镇|站|柜|店|室|楼|园|超市|门|点|中心|物业").matcher(place).find()
                && !place.contains("取件码") && !place.contains("提货码")
                && !place.matches(".*(?:运单|尾号|验证码|请用|请凭|http).*");
    }

    private static String cleanPlace(String place) {
        return place.replaceAll("^[\\s:：\\[【(（]+|[\\s\\]】)）]+$", "")
                .replaceAll("(?:取件码|提货码|请凭|请用).*$", "").trim();
    }

    private static String find(Pattern pattern, String text, int group) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(group).trim() : "";
    }
}
