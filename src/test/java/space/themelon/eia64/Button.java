package space.themelon.eia64;

public class Button {

    public String meow;

    private String text = "";

    public void Text(String text) {
        this.text = text;
    }

    public String Text() {
        return text;
    }

    @Override
    public String toString() {
        return "Button{" +
                "text='" + text + '\'' +
                '}';
    }
}
