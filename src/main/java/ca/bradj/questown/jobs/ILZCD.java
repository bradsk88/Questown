package ca.bradj.questown.jobs;

public interface ILZCD<T> {
    T resolve();

    LZCD.Populated<T> populate();
}
