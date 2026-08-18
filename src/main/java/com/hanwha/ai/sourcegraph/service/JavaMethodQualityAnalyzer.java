package com.hanwha.ai.sourcegraph.service;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

final class JavaMethodQualityAnalyzer {

    MethodQuality analyze(MethodDeclaration method) {
        int startLine = method.getRange().map(range -> range.begin.line).orElse(0);
        int endLine = method.getRange().map(range -> range.end.line).orElse(0);
        int lineCount = startLine > 0 && endLine >= startLine ? endLine - startLine + 1 : 0;

        BlockStmt cleanBody = method.getBody().map(BlockStmt::clone).orElse(null);
        if (cleanBody != null) {
            cleanBody.getAllContainedComments().forEach(Node::remove);
            cleanBody.getOrphanComments().forEach(Node::remove);
        }
        String methodBody = cleanBody == null ? "" : cleanBody.toString();
        String normalizedBody = compactOutsideLiterals(methodBody);
        String structuralBody = structuralBody(method, cleanBody);

        List<Node> branches = branchNodes(method);
        int logicalBranches = logicalBranchCount(method);
        int cyclomaticComplexity = 1 + branches.size() + logicalBranches;
        int cognitiveComplexity = logicalBranches;
        int maxNestingDepth = 0;
        for (Node branch : branches) {
            int depth = 1 + ancestorNestingDepth(branch, method);
            cognitiveComplexity += depth;
            maxNestingDepth = Math.max(maxNestingDepth, depth);
        }

        return new MethodQuality(
                startLine,
                endLine,
                lineCount,
                methodBody,
                normalizedBody,
                sha256(normalizedBody),
                sha256(structuralBody),
                cyclomaticComplexity,
                cognitiveComplexity,
                maxNestingDepth,
                method.getParameters().size(),
                method.findAll(ReturnStmt.class).size(),
                method.findAll(ThrowStmt.class).size(),
                branches.size(),
                method.findAll(MethodCallExpr.class).size()
        );
    }

    private String structuralBody(MethodDeclaration method, BlockStmt cleanBody) {
        if (cleanBody == null) {
            return "";
        }

        BlockStmt structural = cleanBody.clone();
        Set<String> variableNames = new HashSet<>();
        method.getParameters().stream().map(Parameter::getNameAsString).forEach(variableNames::add);
        structural.findAll(VariableDeclarator.class).stream()
                .map(VariableDeclarator::getNameAsString)
                .forEach(variableNames::add);
        structural.findAll(Parameter.class).stream()
                .map(Parameter::getNameAsString)
                .forEach(variableNames::add);

        structural.findAll(NameExpr.class).stream()
                .filter(name -> variableNames.contains(name.getNameAsString()))
                .forEach(name -> name.setName("$variable"));
        structural.findAll(VariableDeclarator.class)
                .forEach(variable -> variable.setName("$variable"));
        structural.findAll(Parameter.class)
                .forEach(parameter -> parameter.setName("$variable"));

        List<LiteralExpr> literals = new ArrayList<>(structural.findAll(LiteralExpr.class));
        literals.forEach(literal -> literal.replace(new NameExpr("$literal")));
        return compactOutsideLiterals(structural.toString());
    }

    private List<Node> branchNodes(MethodDeclaration method) {
        List<Node> branches = new ArrayList<>();
        method.walk(node -> {
            if (isBranch(node)) {
                branches.add(node);
            }
        });
        return branches;
    }

    private boolean isBranch(Node node) {
        return node instanceof IfStmt
                || node instanceof ForStmt
                || node instanceof ForEachStmt
                || node instanceof WhileStmt
                || node instanceof DoStmt
                || node instanceof CatchClause
                || node instanceof SwitchEntry
                || node instanceof ConditionalExpr;
    }

    private boolean isNestingControl(Node node) {
        return isBranch(node) || node instanceof SwitchStmt;
    }

    private int ancestorNestingDepth(Node branch, MethodDeclaration method) {
        int depth = 0;
        Node parent = branch.getParentNode().orElse(null);
        while (parent != null && parent != method) {
            if (isNestingControl(parent)) {
                depth += 1;
            }
            parent = parent.getParentNode().orElse(null);
        }
        return depth;
    }

    private int logicalBranchCount(MethodDeclaration method) {
        return (int) method.findAll(BinaryExpr.class).stream()
                .filter(expression -> expression.getOperator() == BinaryExpr.Operator.AND
                        || expression.getOperator() == BinaryExpr.Operator.OR)
                .count();
    }

    private String compactOutsideLiterals(String source) {
        StringBuilder normalized = new StringBuilder(source.length());
        boolean inString = false;
        boolean inCharacter = false;
        boolean inTextBlock = false;
        boolean escaped = false;

        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            boolean tripleQuote = current == '"'
                    && index + 2 < source.length()
                    && source.charAt(index + 1) == '"'
                    && source.charAt(index + 2) == '"';

            if (!inCharacter && tripleQuote) {
                inTextBlock = !inTextBlock;
                normalized.append("\"\"\"");
                index += 2;
                escaped = false;
                continue;
            }
            if (!inTextBlock && !inCharacter && current == '"' && !escaped) {
                inString = !inString;
            } else if (!inTextBlock && !inString && current == '\'' && !escaped) {
                inCharacter = !inCharacter;
            }

            if (!Character.isWhitespace(current) || inString || inCharacter || inTextBlock) {
                normalized.append(current);
            }
            escaped = (inString || inCharacter) && current == '\\' && !escaped;
            if (current != '\\') {
                escaped = false;
            }
        }
        return normalized.toString();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    record MethodQuality(
            int startLine,
            int endLine,
            int lineCount,
            String methodBody,
            String normalizedBody,
            String methodHash,
            String structuralHash,
            int cyclomaticComplexity,
            int cognitiveComplexity,
            int maxNestingDepth,
            int parameterCount,
            int returnCount,
            int throwCount,
            int branchCount,
            int callCount
    ) {
    }
}
