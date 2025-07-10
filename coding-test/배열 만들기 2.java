    public int[] solution(int l, int r) {
        ArrayList<Integer> list = new ArrayList<>();
        for (int i = l; i <= r; i++) {
            boolean flag = true;
            for (char c : String.valueOf(i).toCharArray()) {
                if (c != '0' && c != '5') {
                    flag = false;
                    break;
                }
            }
            if (flag) list.add(i);

        }


        if (list.isEmpty()) {
            return new int[]{-1};
        }

        int size = list.size();
        int[] answer = new int[size];
        for (int i = 0; i < size; i++) {
            answer[i] = list.get(i);
        }
        return answer;
    }