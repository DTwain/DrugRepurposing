package org.example.repository;

public class RepositoryException extends RuntimeException {
    public RepositoryException(){}

    public RepositoryException(String message){
        super(message);
    }
    public RepositoryException(Exception ex){
        super(ex);
    }

    public RepositoryException(String s, Exception e) {
    }

    public RepositoryException(String threadExecutionInterrupted, InterruptedException e) {
    }
}