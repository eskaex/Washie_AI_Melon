module com.washie.washie {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.washie.washie to javafx.fxml;
    exports com.washie.washie;
}