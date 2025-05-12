package org.example.utils.observer;

import org.example.utils.events.Event;
import java.util.ArrayList;
import java.util.List;

// EventManager class handles any type of event that implements the Event interface
public class EventManager<E extends Event> implements Observable<E> {

    // List to hold registered observers
    private List<Observer<E>> observers = new ArrayList<>();

    @Override
    public void addObserver(Observer<E> observer) {
        observers.add(observer);
    }

    @Override
    public void removeObserver(Observer<E> observer) {
        observers.remove(observer);
    }

    @Override
    public void notifyObservers(E event) {
        for (Observer<E> observer : observers) {
            observer.update(event);  // Notify each observer about the event
        }
    }
}
