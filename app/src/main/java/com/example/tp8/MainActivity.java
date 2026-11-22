package com.example.tp8;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    // PREFERENCES KEYS
    private static final String PREFS_NAME = "SudokuPrefs";
    private static final String KEY_GAMES_WON = "games_won";
    private static final String KEY_BEST_TIME_PREFIX = "best_time_";

    // UI ELEMENTS
    private TextView tvTimer, tvGamesWon, tvBestTime, tvStatus, tvSubtitle;
    private Button btnLevel, btnStart, btnCancel;
    private GridLayout sudokuGridLayout;

    // GAME STATE
    private int gridDimension = 4; // 4x4 default
    private int subRows = 2;       // 2x2 subgrid for 4x4
    private int subCols = 2;
    private long totalTimeMillis = 120000; // 2 minutes for 4x4

    private int[][] solutionGrid;
    private int[][] initialGrid;
    private View[][] cellViews; // Stores TextView or EditText for each cell (r, c)

    private CountDownTimer countDownTimer;
    private boolean isGameRunning = false;
    private long timeRemainingMillis = 0;
    private int gamesWonCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Edge-to-edge window insets handling
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize UI Views
        tvTimer = findViewById(R.id.tvTimer);
        tvGamesWon = findViewById(R.id.tvGamesWon);
        tvBestTime = findViewById(R.id.tvBestTime);
        tvStatus = findViewById(R.id.tvStatus);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        btnLevel = findViewById(R.id.btnLevel);
        btnStart = findViewById(R.id.btnStart);
        btnCancel = findViewById(R.id.btnCancel);
        sudokuGridLayout = findViewById(R.id.sudokuGridLayout);

        // Load saved progress
        loadPreferences();

        // Button Event Listeners
        btnLevel.setOnClickListener(v -> toggleGridLevel());
        btnStart.setOnClickListener(v -> startNewGame());
        btnCancel.setOnClickListener(v -> cancelGame());

        // Prepare initial grid preview
        setupGridForCurrentLevel();
    }

    // ==========================================
    // PREFERENCES & PERSISTENCE (BONUS REQUIREMENT)
    // ==========================================

    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gamesWonCount = prefs.getInt(KEY_GAMES_WON, 0);
        updateGamesWonDisplay();
        updateBestTimeDisplay();
    }

    private void updateGamesWonDisplay() {
        tvGamesWon.setText(String.format(Locale.US, "🎮 %d Games", gamesWonCount));
    }

    private void updateBestTimeDisplay() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long bestTimeSeconds = prefs.getLong(KEY_BEST_TIME_PREFIX + gridDimension, -1);
        if (bestTimeSeconds > 0) {
            long minutes = bestTimeSeconds / 60;
            long seconds = bestTimeSeconds % 60;
            tvBestTime.setText(String.format(Locale.US, "🏆 %02d:%02d", minutes, seconds));
        } else {
            tvBestTime.setText("🏆 --:--");
        }
    }

    private void saveBestTimeIfRecord(long secondsTaken) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String key = KEY_BEST_TIME_PREFIX + gridDimension;
        long currentRecord = prefs.getLong(key, -1);
        if (currentRecord == -1 || secondsTaken < currentRecord) {
            prefs.edit().putLong(key, secondsTaken).apply();
            updateBestTimeDisplay();
            Toast.makeText(this, "🎉 New Best Time Record!", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveGamesWon() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_GAMES_WON, gamesWonCount).apply();
        updateGamesWonDisplay();
    }

    // ==========================================
    // LEVEL SELECTION (PART C REQUIREMENT)
    // ==========================================

    private void toggleGridLevel() {
        if (isGameRunning) {
            cancelGame();
        }

        if (gridDimension == 4) {
            gridDimension = 6;
            subRows = 2;
            subCols = 3;
            totalTimeMillis = 240000; // 4 minutes
            btnLevel.setText("Grid: 6x6");
        } else if (gridDimension == 6) {
            gridDimension = 9;
            subRows = 3;
            subCols = 3;
            totalTimeMillis = 420000; // 7 minutes
            btnLevel.setText("Grid: 9x9");
        } else {
            gridDimension = 4;
            subRows = 2;
            subCols = 2;
            totalTimeMillis = 120000; // 2 minutes
            btnLevel.setText("Grid: 4x4");
        }

        tvSubtitle.setText("Fill each cell with unique digits (1 to " + gridDimension + ")");
        updateBestTimeDisplay();
        setupGridForCurrentLevel();
        tvStatus.setText("Selected " + gridDimension + "x" + gridDimension + " mode. Press Start!");
    }

    private void setupGridForCurrentLevel() {
        generateSudokuPuzzle();
        buildGridLayoutUI();
        setGridEnabled(false);
        updateTimerDisplay(totalTimeMillis);
    }

    // ==========================================
    // PUZZLE GENERATION (BONUS REQUIREMENT)
    // ==========================================

    private void generateSudokuPuzzle() {
        solutionGrid = new int[gridDimension][gridDimension];
        solveBoard(solutionGrid, 0, 0, true);

        initialGrid = new int[gridDimension][gridDimension];
        for (int i = 0; i < gridDimension; i++) {
            System.arraycopy(solutionGrid[i], 0, initialGrid[i], 0, gridDimension);
        }

        // Determine empty cells based on grid dimension
        int cellsToRemove;
        if (gridDimension == 4) {
            cellsToRemove = 8; // 8 empty out of 16
        } else if (gridDimension == 6) {
            cellsToRemove = 18; // 18 empty out of 36
        } else {
            cellsToRemove = 42; // 42 empty out of 81
        }

        Random random = new Random();
        int count = 0;
        while (count < cellsToRemove) {
            int randRow = random.nextInt(gridDimension);
            int randCol = random.nextInt(gridDimension);
            if (initialGrid[randRow][randCol] != 0) {
                initialGrid[randRow][randCol] = 0;
                count++;
            }
        }
    }

    private boolean solveBoard(int[][] board, int row, int col, boolean randomize) {
        if (col == gridDimension) {
            return solveBoard(board, row + 1, 0, randomize);
        }
        if (row == gridDimension) {
            return true;
        }

        if (board[row][col] != 0) {
            return solveBoard(board, row, col + 1, randomize);
        }

        List<Integer> numbers = new ArrayList<>();
        for (int i = 1; i <= gridDimension; i++) {
            numbers.add(i);
        }
        if (randomize) {
            Collections.shuffle(numbers);
        }

        for (int num : numbers) {
            if (isValidPlacement(board, row, col, num)) {
                board[row][col] = num;
                if (solveBoard(board, row, col + 1, randomize)) {
                    return true;
                }
                board[row][col] = 0;
            }
        }
        return false;
    }

    private boolean isValidPlacement(int[][] board, int row, int col, int num) {
        // Check row
        for (int c = 0; c < gridDimension; c++) {
            if (board[row][c] == num) return false;
        }
        // Check column
        for (int r = 0; r < gridDimension; r++) {
            if (board[r][col] == num) return false;
        }
        // Check subgrid box
        int startRow = (row / subRows) * subRows;
        int startCol = (col / subCols) * subCols;
        for (int r = 0; r < subRows; r++) {
            for (int c = 0; c < subCols; c++) {
                if (board[startRow + r][startCol + c] == num) return false;
            }
        }
        return true;
    }

    // ==========================================
    // DYNAMIC GRID UI CREATION (PART A REQUIREMENT)
    // ==========================================

    private void buildGridLayoutUI() {
        sudokuGridLayout.removeAllViews();
        sudokuGridLayout.setRowCount(gridDimension);
        sudokuGridLayout.setColumnCount(gridDimension);

        cellViews = new View[gridDimension][gridDimension];

        // Calculate responsive cell size based on screen width
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int screenWidth = displayMetrics.widthPixels;
        int availableWidth = screenWidth - (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, displayMetrics);
        int cellSizePx = Math.min(availableWidth / gridDimension - 8, (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 65, displayMetrics));
        if (cellSizePx < 70) {
            cellSizePx = Math.max(cellSizePx, (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36, displayMetrics));
        }

        int fontSizeSp = gridDimension == 9 ? 14 : (gridDimension == 6 ? 18 : 22);

        for (int r = 0; r < gridDimension; r++) {
            for (int c = 0; c < gridDimension; c++) {
                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.rowSpec = GridLayout.spec(r);
                params.columnSpec = GridLayout.spec(c);
                params.width = cellSizePx;
                params.height = cellSizePx;
                params.setMargins(4, 4, 4, 4);

                int val = initialGrid[r][c];

                if (val != 0) {
                    // Fixed number (TextView)
                    TextView tv = new TextView(this);
                    tv.setLayoutParams(params);
                    tv.setGravity(Gravity.CENTER);
                    tv.setText(String.valueOf(val));
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp);
                    tv.setTypeface(null, Typeface.BOLD);
                    tv.setTextColor(getColor(R.color.text_fixed));
                    tv.setBackgroundResource(R.drawable.cell_background_fixed);
                    cellViews[r][c] = tv;
                    sudokuGridLayout.addView(tv);
                } else {
                    // Empty editable cell (EditText)
                    EditText et = new EditText(this);
                    et.setLayoutParams(params);
                    et.setGravity(Gravity.CENTER);
                    et.setInputType(InputType.TYPE_CLASS_NUMBER);
                    et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1)});
                    et.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp);
                    et.setTypeface(null, Typeface.BOLD);
                    et.setTextColor(Color.WHITE);
                    et.setBackgroundResource(R.drawable.cell_background);

                    et.addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {}

                        @Override
                        public void afterTextChanged(Editable s) {
                            validateCellAndGrid();
                        }
                    });

                    cellViews[r][c] = et;
                    sudokuGridLayout.addView(et);
                }
            }
        }
    }

    private void setGridEnabled(boolean enabled) {
        for (int r = 0; r < gridDimension; r++) {
            for (int c = 0; c < gridDimension; c++) {
                View cell = cellViews[r][c];
                if (cell instanceof EditText) {
                    cell.setEnabled(enabled);
                    cell.setFocusable(enabled);
                    cell.setFocusableInTouchMode(enabled);
                }
            }
        }
    }

    // ==========================================
    // REAL-TIME VALIDATION & RULES (PART C REQUIREMENT)
    // ==========================================

    private void validateCellAndGrid() {
        if (!isGameRunning) return;

        int[][] currentValues = getCurrentGridValues();

        // Check validation for all empty cells
        boolean allCorrectAndFilled = true;
        boolean hasAnyError = false;

        for (int r = 0; r < gridDimension; r++) {
            for (int c = 0; c < gridDimension; c++) {
                if (initialGrid[r][c] == 0 && cellViews[r][c] instanceof EditText) {
                    EditText et = (EditText) cellViews[r][c];
                    String text = et.getText().toString().trim();

                    if (text.isEmpty()) {
                        allCorrectAndFilled = false;
                        et.setBackgroundResource(R.drawable.cell_background);
                        et.setTextColor(Color.WHITE);
                    } else {
                        int num = Integer.parseInt(text);

                        // Rule: digit must be between 1 and gridDimension
                        if (num < 1 || num > gridDimension || !isCellValid(currentValues, r, c, num)) {
                            et.setBackgroundResource(R.drawable.cell_background_error);
                            et.setTextColor(getColor(R.color.cell_error_text));
                            allCorrectAndFilled = false;
                            hasAnyError = true;
                        } else {
                            et.setBackgroundResource(R.drawable.cell_background);
                            et.setTextColor(Color.WHITE);
                        }
                    }
                }
            }
        }

        if (hasAnyError) {
            tvStatus.setText("⚠️ Highlighted numbers conflict with Sudoku rules!");
            tvStatus.setTextColor(getColor(R.color.cell_error_text));
        } else {
            tvStatus.setText("Keep going! Looking good...");
            tvStatus.setTextColor(getColor(R.color.text_light));
        }

        // Win condition check (PART B REQUIREMENT)
        if (allCorrectAndFilled) {
            handleGameWin();
        }
    }

    private int[][] getCurrentGridValues() {
        int[][] values = new int[gridDimension][gridDimension];
        for (int r = 0; r < gridDimension; r++) {
            for (int c = 0; c < gridDimension; c++) {
                if (initialGrid[r][c] != 0) {
                    values[r][c] = initialGrid[r][c];
                } else if (cellViews[r][c] instanceof EditText) {
                    String str = ((EditText) cellViews[r][c]).getText().toString().trim();
                    if (!str.isEmpty()) {
                        try {
                            values[r][c] = Integer.parseInt(str);
                        } catch (NumberFormatException e) {
                            values[r][c] = 0;
                        }
                    }
                }
            }
        }
        return values;
    }

    private boolean isCellValid(int[][] values, int row, int col, int num) {
        // Check row duplicates
        for (int c = 0; c < gridDimension; c++) {
            if (c != col && values[row][c] == num) return false;
        }
        // Check column duplicates
        for (int r = 0; r < gridDimension; r++) {
            if (r != row && values[r][col] == num) return false;
        }
        // Check subgrid box duplicates
        int startRow = (row / subRows) * subRows;
        int startCol = (col / subCols) * subCols;
        for (int r = 0; r < subRows; r++) {
            for (int c = 0; c < subCols; c++) {
                int curR = startRow + r;
                int curC = startCol + c;
                if ((curR != row || curC != col) && values[curR][curC] == num) {
                    return false;
                }
            }
        }
        return true;
    }

    // ==========================================
    // GAME CONTROLS & TIMER (PART B & C)
    // ==========================================

    private void startNewGame() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        generateSudokuPuzzle();
        buildGridLayoutUI();
        setGridEnabled(true);

        isGameRunning = true;
        btnLevel.setEnabled(false);
        tvStatus.setText("🎮 Game in progress! Good luck!");
        tvStatus.setTextColor(getColor(R.color.text_light));

        startTimer(totalTimeMillis);
    }

    private void cancelGame() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        isGameRunning = false;
        setGridEnabled(false);
        btnLevel.setEnabled(true);
        updateTimerDisplay(totalTimeMillis);
        tvStatus.setText("Game cancelled. Press Start to play.");
        tvStatus.setTextColor(getColor(R.color.text_light));
    }

    private void startTimer(long millis) {
        timeRemainingMillis = millis;
        countDownTimer = new CountDownTimer(millis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeRemainingMillis = millisUntilFinished;
                updateTimerDisplay(millisUntilFinished);
            }

            @Override
            public void onFinish() {
                handleTimeOut();
            }
        }.start();
    }

    private void updateTimerDisplay(long millis) {
        long secondsTotal = millis / 1000;
        long minutes = secondsTotal / 60;
        long seconds = secondsTotal % 60;
        tvTimer.setText(String.format(Locale.US, "⏱ %02d:%02d", minutes, seconds));
    }

    private void handleTimeOut() {
        isGameRunning = false;
        setGridEnabled(false);
        btnLevel.setEnabled(true);
        tvTimer.setText("⏱ 00:00");
        tvStatus.setText("⏰ Time's Up! Game Over.");
        tvStatus.setTextColor(getColor(R.color.cell_error_text));

        new AlertDialog.Builder(this)
                .setTitle("⏰ Time's Up!")
                .setMessage("You ran out of time. Would you like to try again?")
                .setPositiveButton("Try Again", (dialog, which) -> startNewGame())
                .setNegativeButton("Close", null)
                .show();
    }

    private void handleGameWin() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        isGameRunning = false;
        setGridEnabled(false);
        btnLevel.setEnabled(true);

        long timeTakenSeconds = (totalTimeMillis - timeRemainingMillis) / 1000;
        long minutes = timeTakenSeconds / 60;
        long seconds = timeTakenSeconds % 60;
        String timeStr = String.format(Locale.US, "%02d:%02d", minutes, seconds);

        gamesWonCount++;
        saveGamesWon();
        saveBestTimeIfRecord(timeTakenSeconds);

        tvStatus.setText("🎉 Congratulations! You solved the Sudoku!");
        tvStatus.setTextColor(getColor(R.color.cell_success_text));

        new AlertDialog.Builder(this)
                .setTitle("🏆 VICTORY!")
                .setMessage("Fantastic job! You completed the Sudoku puzzle in " + timeStr + "!\n\nTotal Wins: " + gamesWonCount)
                .setPositiveButton("Play Again", (dialog, which) -> startNewGame())
                .setNegativeButton("OK", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}