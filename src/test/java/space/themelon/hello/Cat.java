package space.themelon.hello;

import java.util.Collections;
import java.util.List;

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

  public List<String> AddressesAndNames() {
    return Collections.emptyList();
  }

  public boolean IsMeow() {
    return true;
  }
}
