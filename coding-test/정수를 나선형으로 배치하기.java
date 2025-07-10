    public int[][] solution(int n) {
        int[][] answer = new int[n][n];
        int value = 1;
        int rowStart = 0;
        int colStart = 0;
        int rowEnd = n - 1;
        int colEnd = n - 1;

        while (value <= n * n) {

            for (int i = colStart; i <= colEnd; i++) {
                answer[rowStart][i] = value++;
            }
            rowStart++;

            for (int i = rowStart; i <= rowEnd; i++) {
                answer[i][colEnd] = value++;
            }
            colEnd--;

            for (int i = colEnd; i >= colStart; i--) {
                answer[rowEnd][i] = value++;
            }
            rowEnd--;


            for (int i = rowEnd; i >= rowStart; i--) {
                answer[i][colStart] = value++;
            }
            colStart++;

        }

        return answer;
    }