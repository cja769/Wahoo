package com.jay.wahoo;

import com.jay.wahoo.Player.PlayerState.MarblePosition;

import java.util.ArrayList;
import java.util.List;

public interface ContainingBoard {
    Marble[] getArea();

    default List<MarblePosition> getState(List<Marble> movable) {
        List<MarblePosition> positions = new ArrayList<>();
        Marble[] area = getArea();
        for (int i = 0; i < area.length; i++) {
            Marble marble = area[i];
            if (marble != null) {
                positions.add(MarblePosition.from(marble, movable.contains(marble), i));
            }
        }
        return positions;
    }
}
