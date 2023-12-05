package level1.level2;

public class ClassesHaveSynchronizedBlocks {
    public void syncBlock() {
        synchronized(this){
            System.out.println("hello");
        }
    }
}

class ClassesHaveSynchronizedBlocks2 {
    private Object object;

    public void syncBlock() {
        synchronized (object){
            System.out.println("hello");
        }
    }

    public void syncBlock2() {
        synchronized (object){
            System.out.println("hello");
        }
        System.out.println("outside synchronized block");
    }
}

class ComponentClass {

}
class ClassesHaveSynchronizedBlocks3 {

    private ComponentClass componentClass;

    public void syncBlock() {
        synchronized (componentClass){
            System.out.println("hello");
        }
    }
}
