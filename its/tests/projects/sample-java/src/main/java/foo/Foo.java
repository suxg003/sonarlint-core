package foo;

public class Foo {
  public void call_echo() {
    echo(3);
  }
  
  public void echo(int i) {
    should_be_static();
  }
  
  // invalid
  private void should_be_static() {
    System.out.println("Foo");
  }
  
  String ip = "192.168.12.42"; // Hotspots should not be reported in SonarLint
  
}
