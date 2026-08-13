package models;

import java.util.List;

public record MethodSignature(String returnType, List<String> argumentTypes) {
}
