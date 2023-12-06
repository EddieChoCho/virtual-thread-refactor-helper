import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class VirtualThreadRefactorHelper {

    static Logger logger = Logger.getLogger(VirtualThreadRefactorHelper.class.getName());
    private static final ParserConfiguration.LanguageLevel JAVA_VERSION = ParserConfiguration.LanguageLevel.JAVA_17;

    public static void main(String[] args) throws IOException {

        var scanner = new Scanner(System.in);
        logger.info("Please input the projectPath:");
        var projectPath = scanner.nextLine();
        ParserConfiguration parserConfiguration = new ParserConfiguration();
        parserConfiguration.setLanguageLevel(JAVA_VERSION);

        JavaParser javaParser = new JavaParser(parserConfiguration);

        List<CompilationUnit> unitList = parseJavaFiles(javaParser, projectPath);
        for (CompilationUnit cu : unitList) {
            boolean refactored = refactorToSupportVirtualThreads(javaParser, unitList, cu);
            if (refactored) {
                try (FileOutputStream out = new FileOutputStream(Utils.getFilePath(cu))) {
                    out.write(cu.toString().getBytes());
                } catch (Exception e) {
                    logger.warning("Ｆailed to refactor " + cu);
                }
            }
        }

        logger.info("Java file modified successfully.");


    }

    public static List<CompilationUnit> parseJavaFiles(JavaParser javaParser, String projectPath) {
        List<CompilationUnit> compilationUnits = new ArrayList<>();

        try (Stream<Path> pathsStream = Files.walk(Paths.get(projectPath))) {
            pathsStream.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().endsWith("Tests.java"))
                    .filter(path -> !path.toString().endsWith("Test.java"))
                    .forEach(path -> {
                        try {
                            ParseResult<CompilationUnit> parseResult = javaParser.parse(path);
                            if (parseResult.isSuccessful()) {
                                compilationUnits.add(parseResult.getResult().orElseThrow());
                            } else {
                                logger.warning("Error parsing file: " + path);
                                parseResult.getProblems().forEach(error -> logger.warning(error.toString()));
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }

        return compilationUnits;
    }

    public static boolean refactorToSupportVirtualThreads(JavaParser javaParser, List<CompilationUnit> unitList, CompilationUnit cu) throws IOException {
        boolean refactored = false;
        for (TypeDeclaration<?> type : cu.getTypes()) {
            for (MethodDeclaration method : type.findAll(MethodDeclaration.class)) {
                if (method.isSynchronized()) {
                    refactorSynchronizedMethods(javaParser, cu, type, method);
                    refactored = true;
                }

                if (method.getBody().isPresent()) {
                    for (Statement statement : method.getBody().orElseThrow().getStatements()) {
                        if (statement.isSynchronizedStmt()) {
                            refactorSynchronizeBlocks(javaParser, unitList, cu, type, method, (SynchronizedStmt) statement);
                            refactored = true;
                        }
                    }
                }
            }
        }
        return refactored;
    }

    private static void refactorSynchronizedMethods(JavaParser javaParser, CompilationUnit cu, TypeDeclaration<?> type, MethodDeclaration method) {
        String lockName = ReentrantLockFactory.getReentrantLockName(type, method.isStatic());
        NodeList<BodyDeclaration<?>> members = type.getMembers();
        if (Utils.hasNoField(members, lockName)) {
            members.add(0, ReentrantLockFactory.createReentrantLock(javaParser, cu, type, method.isStatic()));
        }
        removeSynchronizedKeyword(method);
        wrapMethodBodyWithLockAndUnlockStatements(method, lockName);
    }

    private static void removeSynchronizedKeyword(MethodDeclaration method) {
        NodeList<Modifier> modifiers = method.getModifiers();
        Modifier synchronizedKeyword = modifiers.stream()
                .filter(modifier -> modifier.getKeyword() == Modifier.Keyword.SYNCHRONIZED)
                .findFirst()
                .orElseThrow();

        modifiers.remove(synchronizedKeyword);
    }

    private static void wrapMethodBodyWithLockAndUnlockStatements(MethodDeclaration method, String lockName) {
        BlockStmt newBody = new BlockStmt();
        newBody.addStatement(new NameExpr(lockName + ".lock()"));
        BlockStmt originalBody = method.getBody().orElseThrow(() -> new IllegalStateException("Method body not found"));

        BlockStmt finallyBlock = new BlockStmt();
        finallyBlock.addStatement(new NameExpr(lockName + ".unlock()"));
        newBody.addStatement(new TryStmt(originalBody, new NodeList<>(), finallyBlock));
        method.setBody(newBody);
    }

    private static void refactorSynchronizeBlocks(JavaParser javaParser, List<CompilationUnit> unitList, CompilationUnit cu, TypeDeclaration<?> type, MethodDeclaration method, SynchronizedStmt statement) throws IOException {
        Expression expression = statement.getExpression();
        if (expression instanceof ThisExpr) {
            refactorSynchronizeBlockUsingThis(javaParser, cu, type, method, statement);

        } else if (expression instanceof NameExpr nameExpr) {
            refactorSynchronizeBlockUsingField(javaParser, unitList, cu, type, method, statement, nameExpr);

        } else if (expression instanceof ClassExpr classExpr) {
            ClassOrInterfaceType classType = (ClassOrInterfaceType) classExpr.getType();
            SimpleName className = classType.getName();
            CompilationUnit unitDefinedType = Utils.getCompilationUnitByName(unitList, className).orElseThrow();
            refactorSynchronizeBlockUsingLockFromClassOrComponent(javaParser, unitDefinedType, classType, method, statement, className.asString(), true);

        }
    }

    private static void refactorSynchronizeBlockUsingThis(JavaParser javaParser, CompilationUnit cu, TypeDeclaration<?> type, MethodDeclaration method, SynchronizedStmt statement) {
        String lockName = ReentrantLockFactory.getReentrantLockName(type, false);
        if (Utils.hasNoField(type.getMembers(), lockName)) {
            FieldDeclaration lockFiled = ReentrantLockFactory.createReentrantLock(javaParser, cu, type, false);
            type.getMembers().add(0, lockFiled);
        }
        refactorSyncBlockWithLock(method, statement, lockName);
    }

    private static void refactorSynchronizeBlockUsingField(JavaParser javaParser, List<CompilationUnit> unitList, CompilationUnit cu, TypeDeclaration<?> type, MethodDeclaration method, SynchronizedStmt statement, NameExpr nameExpr) throws IOException {
        SimpleName lockObjectName = nameExpr.getName();
        FieldDeclaration lockObjectField = Utils.getFieldDeclaration(type.getMembers(), lockObjectName);
        ClassOrInterfaceType typeOfLockObjectField = (ClassOrInterfaceType) lockObjectField.getElementType();
        Optional<CompilationUnit> unitDefinedType = Utils.getCompilationUnitByName(unitList, typeOfLockObjectField.getName());

        if (unitDefinedType.isPresent()) {
            refactorSynchronizeBlockUsingLockFromClassOrComponent(javaParser, unitDefinedType.orElseThrow(), typeOfLockObjectField, method, statement, lockObjectName.asString(), false);
        } else {
            refactorSynchronizeBlockUsingObject(javaParser, cu, type, method, statement, lockObjectName);
        }
    }

    private static void refactorSynchronizeBlockUsingLockFromClassOrComponent(JavaParser javaParser, CompilationUnit unitDefinedType, ClassOrInterfaceType classType, MethodDeclaration method, SynchronizedStmt statement, String lockNamePrefix, boolean isStatic) throws IOException {
        TypeDeclaration<?> typeDeclaration = Utils.getTypeDeclaration(unitDefinedType, classType.getName());
        String lockName = ReentrantLockFactory.getReentrantLockName(typeDeclaration, isStatic);
        final FieldDeclaration lockFiled;
        if (Utils.hasNoField(typeDeclaration.getMembers(), lockName)) {
            lockFiled = ReentrantLockFactory.createReentrantLock(javaParser, unitDefinedType, typeDeclaration, isStatic);
            typeDeclaration.getMembers().add(0, lockFiled);
        } else {
            lockFiled = Utils.getField(typeDeclaration.getMembers(), lockName);
        }
        changeFieldToPublic(lockFiled);
        try (FileOutputStream out = new FileOutputStream(Utils.getFilePath(unitDefinedType))) {
            out.write(unitDefinedType.toString().getBytes());
        }

        refactorSyncBlockWithLock(method, statement, lockNamePrefix + "." + lockName);
    }

    private static void refactorSynchronizeBlockUsingObject(JavaParser javaParser, CompilationUnit cu, TypeDeclaration<?> type, MethodDeclaration method, SynchronizedStmt statement, SimpleName lockObjectName) {
        String lockName = ReentrantLockFactory.getReentrantLockName(lockObjectName.asString());
        if (Utils.hasNoField(type.getMembers(), lockName)) {
            FieldDeclaration lockFiled = ReentrantLockFactory.createReentrantLock(javaParser, cu, lockObjectName.asString());
            type.getMembers().add(0, lockFiled);
        }
        refactorSyncBlockWithLock(method, statement, lockName);
    }

    private static void refactorSyncBlockWithLock(MethodDeclaration method, SynchronizedStmt statement, String lockName) {
        BlockStmt statementInsideBlock = new BlockStmt();
        statementInsideBlock.addStatement(statement.getBody().getStatements().get(0));

        BlockStmt newBody = new BlockStmt();
        newBody.addStatement(new NameExpr(lockName + ".lock()"));
        BlockStmt finallyBlock = new BlockStmt();
        finallyBlock.addStatement(new NameExpr(lockName + ".unlock()"));
        newBody.addStatement(new TryStmt(statementInsideBlock, new NodeList<>(), finallyBlock));
        method.getBody().orElseThrow().replace(statement, newBody);
    }

    private static void changeFieldToPublic(FieldDeclaration field) {
        field.removeModifier(Modifier.Keyword.PRIVATE);
        if (!field.hasModifier(Modifier.Keyword.PUBLIC)) {
            field.getModifiers().add(0, new Modifier(Modifier.Keyword.PUBLIC));
        }
    }

}
