module com.washie.washie {
    requires javafx.controls;
    requires javafx.fxml;
    requires jakarta.persistence;


    opens com.washie.washie to javafx.fxml;
    exports com.washie.washie;
}