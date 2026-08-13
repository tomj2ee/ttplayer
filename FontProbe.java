import java.awt.*;
public class FontProbe {
    public static void main(String[] a) {
        Font[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
        System.out.println("total fonts: " + fonts.length);
        for (Font f : fonts) {
            boolean zh = f.canDisplay(0x4e2d) && f.canDisplay(0x6587);
            boolean ko = f.canDisplay(0xd55c) && f.canDisplay(0xd3c0);
            if (ko) {
                System.out.println("KOREAN_OK " + f.getFontName() + " | zh=" + zh + " | family=" + f.getFamily());
            }
        }
        System.out.println("--- named lookups ---");
        String[] names = {"微软雅黑","Microsoft YaHei","宋体","SimSun","Malgun Gothic","맑은 고딕","Gulim","Batang","Apple SD Gothic Neo","Noto Sans KR"};
        for (String n : names) {
            Font f = new Font(n, Font.PLAIN, 12);
            boolean ko = f.canDisplay(0xd55c) && f.canDisplay(0xd3c0);
            System.out.println(n + " -> name=" + f.getFontName() + " ko=" + ko);
        }
    }
}
