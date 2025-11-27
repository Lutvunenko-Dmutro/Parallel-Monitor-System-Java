import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Головний клас програми, що створює та показує GUI.
 * ВЕРСІЯ: Проста, без вкладок.
 */
public class MainApp {

    // --- Налаштування обчислень ---
    private static final int MATRIX_SIZE = 20000;
    private static final double ZERO_CHANCE = 0.3;
    private static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors();

    // --- Компоненти GUI (поля класу) ---
    private JFrame frame;
    private JButton startButton;
    private JPanel centerPanel; // Панель, що динамічно змінює вміст
    private JPanel placeholderPanel; // Початкова "заглушка"
    private JPanel progressPanel;    // Панель з прогрес-баром
    private JProgressBar progressBar;
    private JLabel statusLabel;
    
    private ChartPanel chartPanel; 

    // --- Логіка ---
    private final MatrixLabLogic logic = new MatrixLabLogic();
    private CalculationWorker activeWorker;

    public static void main(String[] args) {
        // Запускаємо GUI в потоці обробки подій (EDT)
        SwingUtilities.invokeLater(() -> new MainApp().createAndShowGUI());
    }

    /**
     * POJO (record) для передачі повного результату
     * від SwingWorker до EDT.
     */
    private record FullBenchmarkResult(
            MatrixLabLogic.CalculationResult serial,
            MatrixLabLogic.CalculationResult parallel) {
    }

    /**
     * Ініціалізує та показує GUI.
     */
    private void createAndShowGUI() {
        setupFlatLaf();

        // Використовуємо ваше ім'я у заголовку для персоналізації
        frame = new JFrame("Практична робота №9 (Варіант 8) - " + "Д. Литвиненко");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(800, 600));

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // --- Створення компонентів ---
        createPlaceholderPanel(); // Створює this.placeholderPanel
        createProgressPanel();    // Створює this.progressPanel

        centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(placeholderPanel, BorderLayout.CENTER);

        JPanel controlPanel = createControlPanel();

        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(controlPanel, BorderLayout.SOUTH);

        frame.add(mainPanel);
        frame.setLocationRelativeTo(null); // Центрувати вікно
        frame.setVisible(true);
    }

    /**
     * Налаштовує темну тему FlatLaf.
     */
    private void setupFlatLaf() {
        try {
            FlatDarkLaf.setup();
            UIManager.put("Button.arc", 999);
            UIManager.put("Component.arc", 12);
            UIManager.put("ProgressBar.arc", 999);
            UIManager.put("TextComponent.arc", 12);
        } catch (Exception ex) {
            System.err.println("Не вдалося встановити FlatLaf Look and Feel");
            ex.printStackTrace();
        }
    }

    /**
     * Створює нижню панель з кнопкою "Старт".
     */
    private JPanel createControlPanel() {
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        startButton = new JButton("Почати обчислення");
        startButton.setFont(new Font("Arial", Font.BOLD, 16));
        startButton.setPreferredSize(new Dimension(250, 40));

        startButton.addActionListener(e -> startCalculation());
        
        controlPanel.add(startButton);
        return controlPanel;
    }

    /**
     * Створює початкову "заглушку" для центральної панелі.
     */
    private void createPlaceholderPanel() {
        placeholderPanel = new JPanel(new GridBagLayout()); // Для центрування
        JLabel placeholderLabel = new JLabel(
                "<html><div style='text-align: center;'>" +
                "Тут буде візуалізація<br>(графік прискорення)" +
                "</div></html>",
                SwingConstants.CENTER);
        
        placeholderLabel.setFont(new Font("Arial", Font.PLAIN, 18));
        placeholderLabel.setForeground(new Color(100, 100, 100));
        
        // Пунктирний бордер
        Border dashedBorder = BorderFactory.createDashedBorder(
                new Color(70, 70, 70), 5, 2, 0, false);
        placeholderLabel.setBorder(BorderFactory.createCompoundBorder(
                dashedBorder,
                BorderFactory.createEmptyBorder(60, 120, 60, 120)
        ));
        
        // placeholderLabel.add(placeholderLabel); // ПОМИЛКА БУЛА ТУТ
        placeholderPanel.add(placeholderLabel); // ВИПРАВЛЕНО: Додаємо JLabel до placeholderPanel
    }

    /**
     * Створює панель прогресу (але ще не показує її).
     */
    private void createProgressPanel() {
        progressPanel = new JPanel(new GridBagLayout()); // Для центрування
        
        JPanel innerPanel = new JPanel();
        innerPanel.setLayout(new BoxLayout(innerPanel, BoxLayout.Y_AXIS));
        
        statusLabel = new JLabel("Підготовка...", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 16));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setFont(new Font("Arial", Font.BOLD, 14));
        progressBar.setPreferredSize(new Dimension(400, 30));
        progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        innerPanel.add(statusLabel);
        innerPanel.add(Box.createRigidArea(new Dimension(0, 15)));
        innerPanel.add(progressBar);
        
        progressPanel.add(innerPanel);
    }

    /**
     * Запускає процес обчислення.
     * Викликається при натисканні кнопки.
     */
    private void startCalculation() {
        startButton.setEnabled(false);
        startButton.setText("Обчислення...");
        
        // Очищуємо центральну панель від попереднього вмісту
        centerPanel.removeAll();
        // Додаємо панель прогресу
        centerPanel.add(progressPanel, BorderLayout.CENTER);
        
        // "Оновлюємо" GUI
        centerPanel.revalidate();
        centerPanel.repaint();

        // Створюємо та запускаємо новий SwingWorker
        activeWorker = new CalculationWorker();
        activeWorker.execute();
    }

    /**
     * Клас SwingWorker для виконання обчислень у фоновому потоці.
     */
    private class CalculationWorker extends SwingWorker<FullBenchmarkResult, String> {

        @Override
        protected FullBenchmarkResult doInBackground() throws Exception {
            // --- Етап 1: Генерація матриці ---
            publish("Генерація матриці..."); // Надіслати в process()
            double[][] matrix = MatrixLabLogic.generateMatrix(MATRIX_SIZE, ZERO_CHANCE);
            
            // --- Етап 2: Послідовний розрахунок ---
            publish("Запуск послідовної версії...");
            MatrixLabLogic.CalculationResult serialResult = logic.calculateSerial(matrix);

            // --- Етап 3: Паралельний розрахунок ---
            publish("Запуск паралельної версії...");
            MatrixLabLogic.CalculationResult parallelResult = 
                    logic.calculateParallel(matrix, THREAD_COUNT);
            
            publish("Завершено.");
            
            return new FullBenchmarkResult(serialResult, parallelResult);
        }

        @Override
        protected void process(List<String> chunks) {
            // Цей метод виконується в EDT
            // Отримуємо останнє повідомлення зі списку
            String latestStatus = chunks.get(chunks.size() - 1);
            statusLabel.setText(latestStatus);
            
            // Імітуємо прогрес на барі
            if (latestStatus.startsWith("Генерація")) {
                progressBar.setValue(10);
                progressBar.setString("10% - Генерація матриці...");
            } else if (latestStatus.startsWith("Запуск послідовної")) {
                progressBar.setValue(40);
                progressBar.setString("40% - Послідовний розрахунок...");
            } else if (latestStatus.startsWith("Запуск паралельної")) {
                progressBar.setValue(80);
                progressBar.setString("80% - Паралельний розрахунок...");
            } else if (latestStatus.startsWith("Завершено")) {
                progressBar.setValue(100);
                progressBar.setString("100% - Завершено");
            }
        }

        @Override
        protected void done() {
            // Цей метод виконується в EDT після завершення doInBackground()
            try {
                // Отримуємо результат з фонового потоку
                FullBenchmarkResult result = get();
                
                // Розраховуємо показники
                double serialTimeMs = result.serial.durationNs() / 1_000_000.0;
                double parallelTimeMs = result.parallel.durationNs() / 1_000_000.0;
                double speedUp = serialTimeMs / parallelTimeMs;

                // --- Відображення результатів ---
                // Прибираємо панель прогресу
                centerPanel.removeAll();
                
                // Створюємо нашу нову панель графіка
                chartPanel = new ChartPanel(serialTimeMs, parallelTimeMs, speedUp);
                
                centerPanel.add(chartPanel, BorderLayout.CENTER);

            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                // У повноцінному додатку тут
                // має бути діалогове вікно з помилкою
                statusLabel.setText("Помилка обчислення!");
                progressBar.setValue(0);
            }
            
            // "Оновлюємо" GUI
            centerPanel.revalidate();
            centerPanel.repaint();
            
            // Повертаємо кнопку в початковий стан
            startButton.setEnabled(true);
            startButton.setText("Почати обчислення");
        }
    }
}