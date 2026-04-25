package com.jservlet.nexus.shared.exceptions;

public class NexusServiceUnavailableException extends RuntimeException {

    public NexusServiceUnavailableException(String message) { super(message); }
    public NexusServiceUnavailableException(String message, Throwable cause) { super(message, cause); }
    public NexusServiceUnavailableException(Throwable cause) { super(cause); }

}
