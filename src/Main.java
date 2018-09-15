import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import persistence.DataManager;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception{
        DataManager.getDataManager().init();
        Parent root = FXMLLoader.load(getClass().getResource("ui/view/MainView.fxml"));
        primaryStage.setTitle("Spusic");
        primaryStage.setScene(new Scene(root, 800, 600));
        primaryStage.show();
    }


    public static void main(String[] args) {
        launch(args);
    }
}
