package dev.jdktrustmanager.ui;

import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;

public final class MainView extends TabPane {

    public MainView() {
        this(new Pane());
    }

    public MainView(Node... pages) {
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
        int index = 0;
        for (String name : Screens.NAMES) {
            Node page = index < pages.length && pages[index] != null ? pages[index] : new Pane();
            getTabs().add(new Tab(name, page));
            index++;
        }
    }
}
