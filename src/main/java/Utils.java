import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.SimpleName;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class Utils {

    public static Optional<CompilationUnit> getCompilationUnitByName(List<CompilationUnit> unitList, SimpleName className) {
        return unitList.stream()
                .filter(unit -> unit.getTypes().stream()
                        .anyMatch(typeDeclaration -> typeDeclaration.getName().equals(className)))
                .findFirst();
    }

    public static TypeDeclaration getTypeDeclaration(CompilationUnit compilationUnit, SimpleName className) {
        return compilationUnit.getTypes().stream()
                .filter(typeDeclaration -> typeDeclaration.getName().equals(className))
                .findFirst()
                .orElseThrow();
    }

    public static FieldDeclaration getFieldDeclaration(NodeList<BodyDeclaration<?>> members, SimpleName name) {
        return members.stream()
                .filter(FieldDeclaration.class::isInstance)
                .map(FieldDeclaration.class::cast)
                .filter(member -> member.getVariables().stream().anyMatch(variableDeclarator -> variableDeclarator.getName().equals(name)))
                .findFirst().orElseThrow();
    }

    public static boolean hasNoField(NodeList<BodyDeclaration<?>> members, String fieldName) {
        return members.stream()
                .filter(FieldDeclaration.class::isInstance)
                .map(FieldDeclaration.class::cast)
                .map(FieldDeclaration::getVariables)
                .flatMap(Collection::stream)
                .noneMatch(variable -> fieldName.equals(variable.getName().asString()));
    }

    public static FieldDeclaration getField(NodeList<BodyDeclaration<?>> members, String fieldName) {
        return members.stream()
                .filter(FieldDeclaration.class::isInstance)
                .map(FieldDeclaration.class::cast)
                .filter(field -> field.getVariables().stream()
                        .anyMatch(variable -> fieldName.equals(variable.getName().asString())))
                .findFirst()
                .orElseThrow();
    }

    public static String getFilePath(CompilationUnit cu) {
        return cu.getStorage().orElseThrow().getDirectory() + "/" + cu.getStorage().orElseThrow().getFileName();
    }
}
