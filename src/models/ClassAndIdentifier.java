package models;

import java.util.Objects;

public class ClassAndIdentifier {
    private String className;
    private String identifier;
    public ClassAndIdentifier(String className, String identifier){
        this.className = className;
        this.identifier = identifier;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClassAndIdentifier that = (ClassAndIdentifier)o;
        return Objects.equals(className, that.className) && Objects.equals(identifier, that.identifier);
    }
    @Override
    public int hashCode() {
        return Objects.hash(className, identifier);
    }
}
