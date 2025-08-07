import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;

public class GeneticAlgorithmTSP extends JPanel {
    static final int POPULATION_SIZE = 20;
    static final int NUM_EXECUTION = 200;

    static double[] x;
    static double[] y;
    static List<List<Integer>> population = new ArrayList<>();
    static List<List<Integer>> tour = new ArrayList<>();
    static double[][] dCidade;
    static double[] distance;
    static List<Integer> parentsOne;
    static List<Integer> parentsTwo;
    static List<Double> costByExecution = new ArrayList<>();

    static final String DB_URL = "jdbc:mysql://localhost:3306/Student";
    static final String DB_USER = "root";
    static final String DB_PASSWORD = "Anisha12";

    public static void main(String[] args) {
        loadMetroRoutes(); // Load metro route data from the database
        generateFirstPopulation();
        generateTour();

        for (int i = 0; i < NUM_EXECUTION; i++) {
            List<Entry<Integer, Double>> sorted_x = fitnessFunction();
            rouletteFunction(sorted_x);
            doCycle(sorted_x);
            generateTour();
            costByExecution.add(sorted_x.get(0).getValue());
        }

        List<Entry<Integer, Double>> sorted_x = fitnessFunction();
        double optimalCost = sorted_x.get(0).getValue();
        List<Integer> bestRoute = population.get(sorted_x.get(0).getKey());
        System.out.println("Optimal path cost: " + optimalCost);
        System.out.print("Best Route: ");
        for (int stationIndex : bestRoute) {
            System.out.print(stationNames.get(stationIndex) + " -> ");
        }
        System.out.println("End");

        // Display results with GUI
        JFrame frame = new JFrame();
        GeneticAlgorithmTSP tsp = new GeneticAlgorithmTSP();
        frame.add(tsp);
        frame.setSize(800, 800);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    static Map<Integer, String> stationNames = new HashMap<>();

    static void loadMetroRoutes() {
        String query = "SELECT * FROM metro_routes";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            List<Double> xList = new ArrayList<>();
            List<Double> yList = new ArrayList<>();
            int index = 0; // For mapping station indices

            while (rs.next()) {
                stationNames.put(++index, rs.getString("city_name"));
                xList.add(rs.getDouble("x_coordinate"));
                yList.add(rs.getDouble("y_coordinate"));
            }

            if (!xList.isEmpty()) {
                x = xList.stream().mapToDouble(Double::doubleValue).toArray();
                y = yList.stream().mapToDouble(Double::doubleValue).toArray();
                dCidade = new double[stationNames.size()][stationNames.size()];
                distance = new double[POPULATION_SIZE];
                System.out.println("Loaded " + stationNames.size() + " metro routes from the database.");
            } else {
                throw new RuntimeException("No metro routes found in the database!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(Color.RED);

        for (int i = 0; i < x.length; i++) {
            int xCoord = (int) (x[i] * 700); // Scale to fit in the window
            int yCoord = (int) (y[i] * 700); // Scale to fit in the window
            g.fillOval(xCoord, yCoord, 7, 7);
            String stationName = stationNames.get(i + 1); // Get name from map
            g.drawString(stationName, xCoord + 5, yCoord);
        }

        g.setColor(Color.GREEN); // Color for the starting point
        int startX = (int) (x[0] * 700);
        int startY = (int) (y[0] * 700);
        g.fillOval(startX, startY, 7, 7);
        g.drawString("Start: " + stationNames.get(1), startX + 10, startY);

        g.setColor(Color.BLUE); // Color for the path
        List<Integer> bestTour = tour.get(0);
        for (int i = 0; i < bestTour.size() - 1; i++) {
            int city1 = bestTour.get(i) - 1;
            int city2 = bestTour.get(i + 1) - 1;
            int x1 = (int) (x[city1] * 700);
            int y1 = (int) (y[city1] * 700);
            int x2 = (int) (x[city2] * 700);
            int y2 = (int) (y[city2] * 700);
            g.drawLine(x1, y1, x2, y2);
        }
    }

    static void generateFirstPopulation() {
        for (int i = 0; i < POPULATION_SIZE; i++) {
            generatePossiblePath();
        }
    }

    static void generatePossiblePath() {
        List<Integer> path = new ArrayList<>();
        Random random = new Random();
        Set<Integer> visitedCities = new HashSet<>();

        path.add(1); // Start with city 1
        visitedCities.add(1);

        for (int i = 1; i < x.length; i++) {
            int randomNum;
            do {
                randomNum = random.nextInt(x.length - 1) + 2; // Choose from city 2 to city N
            } while (visitedCities.contains(randomNum)); // Ensure no duplicates
            path.add(randomNum);
            visitedCities.add(randomNum);
        }
        population.add(path);
    }

    static void generateTour() {
        tour.clear();
        for (List<Integer> ways : population) {
            List<Integer> newTour = new ArrayList<>(ways);
            // Do not add the first city again at the end (no cycle)
            tour.add(newTour);
        }
    }

    static List<Entry<Integer, Double>> fitnessFunction() {
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < x.length; j++) {
                dCidade[i][j] = Math.sqrt(Math.pow(x[i] - x[j], 2) + Math.pow(y[i] - y[j], 2));
            }
        }
        return calculateDistance();
    }

    static List<Entry<Integer, Double>> calculateDistance() {
        Arrays.fill(distance, 0);
        for (int i = 0; i < population.size(); i++) {
            for (int j = 0; j < population.get(i).size() - 1; j++) {
                int firstPos = population.get(i).get(j) - 1;
                int secondPos = population.get(i).get(j + 1) - 1;
                distance[i] += dCidade[firstPos][secondPos];
            }
        }

        Map<Integer, Double> dictDist = new HashMap<>();
        for (int i = 0; i < distance.length; i++) {
            dictDist.put(i, distance[i]);
        }

        List<Entry<Integer, Double>> sorted = new ArrayList<>(dictDist.entrySet());
        sorted.sort(Entry.comparingByValue());
        return sorted;
    }

    static void rouletteFunction(List<Entry<Integer, Double>> sorted_x) {
        List<Integer> arr = new ArrayList<>();
        List<Integer> rouletteArr = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            arr.add(sorted_x.get(i).getKey());
        }
        for (int j = 0; j < arr.size(); j++) {
            for (int k = 0; k < 10 - j; k++) {
                rouletteArr.add(arr.get(j));
            }
        }
        parentsOne = population.get(rouletteArr.get(0));
        parentsTwo = population.get(rouletteArr.get(1));
    }

    static void doCycle(List<Entry<Integer, Double>> sorted_x) {
        Random random = new Random();
        List<List<Integer>> children = new ArrayList<>();

        for (int i = 0; i < POPULATION_SIZE; i++) {
            children.add(crossover(parentsOne, parentsTwo));
        }

        mutate(children);
        population = children;
    }

    static List<Integer> crossover(List<Integer> parent1, List<Integer> parent2) {
        Random random = new Random();
        List<Integer> child = new ArrayList<>();
        int startIdx = random.nextInt(parent1.size());
        int endIdx = random.nextInt(startIdx, parent1.size());

        for (int i = startIdx; i <= endIdx; i++) {
            child.add(parent1.get(i));
        }

        for (int i = 0; i < parent2.size(); i++) {
            if (!child.contains(parent2.get(i))) {
                child.add(parent2.get(i));
            }
        }

        // Ensure first and last cities are not the same
        if (child.get(0).equals(child.get(child.size() - 1))) {
            child.remove(child.size() - 1);  // Remove the last city if it's the same as the first
        }

        return child;
    }

    static void mutate(List<List<Integer>> children) {
        Random random = new Random();
        for (List<Integer> child : children) {
            if (random.nextDouble() < 0.1) {  // Mutation probability
                int i = random.nextInt(child.size());
                int j = random.nextInt(child.size());
                Collections.swap(child, i, j);

                // Ensure first and last cities are not the same
                if (child.get(0).equals(child.get(child.size() - 1))) {
                    Collections.swap(child, i, j); // Swap back if same
                }
            }
        }
    }
}
