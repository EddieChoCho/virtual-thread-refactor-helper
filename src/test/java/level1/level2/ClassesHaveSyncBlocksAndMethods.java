package level1.level2;

public class ClassesHaveSyncBlocksAndMethods {

    public void syncBlock() {
        synchronized(this){
            System.out.println("hello");
        }
        System.out.println("outside syncBlock");
    }

    public synchronized void syncMethod() {
        System.out.println("hello");
    }

}

class NonStaticComponent {

    public void syncBlock() {
        synchronized(this){
            System.out.println("hello");
        }
        System.out.println("outside syncBlock");
    }

    public synchronized void syncMethod() {
        System.out.println("hello");
    }
}
class ClassesHaveSyncBlocksAndMethods2 {

    private NonStaticComponent component;
    public void syncBlock() {
        synchronized(component){
            System.out.println("hello");
        }
        System.out.println("outside syncBlock");
    }

}


class StaticComponent {

    public static synchronized void syncMethod() {
        System.out.println("hello");
    }
}

class ClassesHaveSyncBlocksAndMethods3 {

    private static StaticComponent component;
    public void syncBlock() {
        synchronized(component){
            System.out.println("hello");
        }
        System.out.println("outside syncBlock");
    }

}