import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class ReplaceSyncBlockWithReentrantLockRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceSyncBlockWithReentrantLockRecipe());
    }

    @Test
    void testClassHasNonStaticStaticSynchronizedMethod() {
        rewriteRun(
                java(
                        """
                                    import java.util.List;
                                    class FooBar {
                                        private synchronized void hi() {
                                            System.out.println("hi");
                                        }
                                    }
                                """,
                        """
                                    import java.util.List;
                                    class FooBar {
                                        private void hi() {
                                            System.out.println("hi");
                                        }
                                    }
                                """
                )
        );
    }

}
