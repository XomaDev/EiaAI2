package space.themelon.hello;

public class Cat {

  public final String name;

  public Cat(String name) {
    this.name = name;
  }

  public static void meow(int a) {
    System.out.println("Boww!");
  }

  public static void meow(String a) {
    System.out.println("Meow!");
  }

  public static void sayMeow(CharSequence name) {
    System.out.println("Meow! " + name);
  }
}
