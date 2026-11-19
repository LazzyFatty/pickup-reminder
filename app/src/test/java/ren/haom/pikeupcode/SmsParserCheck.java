package ren.haom.pikeupcode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public final class SmsParserCheck {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static Parcel one(String sms, String code) {
        List<Parcel> list = SmsParser.parse(sms);
        check(list.size() == 1, "Expected one parcel: " + sms);
        check(list.get(0).code.equals(code), "Wrong code: " + list.get(0).code);
        return list.get(0);
    }
    private static void no(String sms) { check(SmsParser.parse(sms).isEmpty(), "False positive: " + sms); }

    public static void main(String[] args) throws Exception {
        Parcel p = one("【顺丰速运】包裹已到松风路88号云杉驿站，取件码：8-2-1456，运单号SF123456789012，联系电话13800138000，详情https://example.org/pick?a=1。", "8-2-1456");
        check(p.company.equals("顺丰速运"), "company");
        check(p.station.equals("云杉驿站"), "station: " + p.station);
        check(p.address.equals("松风路88号云杉驿站"), "address: " + p.address);
        check(p.tracking.equals("SF123456789012"), "tracking");
        check(p.phone.equals("13800138000"), "phone");
        check(p.url.equals("https://example.org/pick?a=1"), "url");
        p = one("【自提通知】您的快递:*54321已到青禾路22号，请用提货码12345678取包裹，咨询快递员", "12345678");
        check(p.tracking.equals("尾号 54321"), "masked tracking: " + p.tracking);
        check(p.address.equals("青禾路22号"), "numbered address");
        check(p.station.isEmpty(), "Do not invent station");
        p = one("请凭取件码87654321到青禾路22号取运单尾号为56789的包裹", "87654321");
        check(p.tracking.equals("尾号 56789"), "tracking tail");
        check(p.address.equals("青禾路22号"), "destination before 取运单: " + p.address);
        one("您的快递已到青禾路22号，请用提货码1234—5678取包裹", "1234-5678");
        p = one("【云杉驿站】您的包裹已到站，凭4-4-2006到松风小区二区3号楼原邻里店取件。", "4-4-2006");
        check(p.address.equals("松风小区二区3号楼原邻里店"), "implicit destination: " + p.address);
        one("【云杉驿站】包裹已到，取件码【Ａ３－１２－８８８】。", "A3-12-888");
        one("取货密码为123456，包裹在自提柜。", "123456");
        one("快递开柜码：a8_9912，请及时取件。", "A8-9912");
        one("取件码：7 - 12 - 330，包裹已到。", "7-12-330");
        p = one("【极兔速递】快件已到。取件码123456，取件地址：春溪路5号；咨询电话010-88886666。", "123456");
        check(p.phone.equals("010-88886666"), "landline");
        check(p.address.equals("春溪路5号"), "explicit address");
        p = one("快递取件码999888，驿站名称：云杉服务点，驿站地址：春溪路5号。", "999888");
        check(p.station.equals("云杉服务点"), "explicit station");
        p = one("包裹取件码123456，电话400-123-4567，网址www.example.org/p。", "123456");
        check(p.phone.equals("400-123-4567"), "400 phone");
        check(p.url.equals("www.example.org/p"), "www URL");
        check(SmsParser.parse("包裹取件码：123456、654321，请到云杉驿站取件。").size() == 2, "multiple codes");
        check(SmsParser.parse("快递取件码123456，取件码654321。").size() == 2, "repeated labels");
        check(SmsParser.parse("快递取件码123456，取件码123456。").size() == 1, "dedup code");
        no("【快递】您好，您的快递123456789012345，已放在服务柜旁边地上");
        no("【快递】您的包裹12345678901234已送至家门口，如未收到请联系快递员");
        no("您的包裹已到驿站，点击https://example.org/123456查看取件信息");
        no("您的登录验证码123456，快递账户登录请勿泄露");
        no("快递运单号123456789012，联系电话13800138000");
        no("请凭123456登录快递账户");
        no("取件码13800138000，请到驿站取件");
        no("快递取件码123456789012345，包裹已到");
        no("快递取件码ABCDEF，包裹已到");
        no("快递取件码12，包裹已到");
        no(null); no("");
        one("您的包裹已到，凭123456前往云杉驿站取件。", "123456");
        one("请凭123456取快递。", "123456");
        check(one("包裹取件码123456，详情https://example.org/EMS", "123456").company.isEmpty(), "Do not extract company from URL");
        p = one("取件码 01234567\n云杉快递柜 | 松风路88号\n", "01234567");
        check(p.station.equals("云杉快递柜"), "standalone locker: " + p.station);
        check(p.address.equals("松风路88号"), "pipe-separated address: " + p.address);
        check(p.company.isEmpty(), "Locker brand is not a courier");
        check(p.phone.isEmpty() && p.tracking.isEmpty() && p.url.isEmpty(), "Do not invent missing fields");
        p = one("取件码 ０１２３４５６７\r\n  青禾驿站 ｜ 春溪街６号  \r\n", "01234567");
        check(p.station.equals("青禾驿站") && p.address.equals("春溪街6号"), "fullwidth pipe and CRLF");
        p = one("取件码 01234567\\\n云杉快递柜 | 松风路88号\\\n\\\n", "01234567");
        check(p.station.equals("云杉快递柜") && p.address.equals("松风路88号"), "pasted line-break markers");
        p = one("取件码01234567，取件地址：春溪路5号；驿站名称：青禾服务点。\n云杉快递柜 | 松风路88号", "01234567");
        check(p.station.equals("青禾服务点") && p.address.equals("春溪路5号"), "Explicit fields take priority");
        p = one("取件码01234567\n云杉快递柜 | 联系电话13800138000", "01234567");
        check(p.station.isEmpty() && p.address.isEmpty(), "Phone is not a pipe-separated place");
        check(p.phone.equals("13800138000"), "Phone remains a phone");
        p = one("取件码01234567\n请联系云杉快递柜 | 松风路88号", "01234567");
        check(p.station.isEmpty() && p.address.isEmpty(), "Instruction is not a bare location row");
        p = one("取件码01234567\n云杉快递柜 | https://example.org/p", "01234567");
        check(p.station.isEmpty() && p.address.isEmpty(), "URL is not an address");
        no("云杉快递柜 | 松风路88号\n运单号SF0123456789");
        no("登录验证码01234567\n云杉快递柜 | 松风路88号");
        no("快递取件码" + new String(new char[20001]).replace('\0', '1'));
        if (args.length > 0) {
            List<String> lines = Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8);
            int messages = 0, accepted = 0;
            for (String line : lines) if (!line.trim().isEmpty()) { messages++; accepted += SmsParser.parse(line).size(); }
            check(messages == 7 && accepted == 4, "Attachment: expected 4 parcels from 7 messages, got " + accepted);
            System.out.println("User attachment: 7 messages, 4 code-bearing parcels, 3 excluded.");
        }
        System.out.println("SmsParserCheck: " + checks + " checks passed.");
    }
}
