package com.jay.wahoo;

import com.jay.wahoo.Player.PlayerState.MarblePosition;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public interface ContainingBoard extends Board {
    default boolean contains(Marble m) {
        return findMarblePosition(m).isPresent();
    }

    default Optional<Integer> findMarblePosition(Marble m) {
        for (int i = 0; i < getArea().length; i++) {
            if (getArea()[i] != null && getArea()[i].equals(m)) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    default List<MarblePosition> getState(List<Marble> isMovable) {
        List<MarblePosition> positions = new ArrayList<>();
        for (int i = 0; i < getArea().length; i++) {
            if (getArea()[i] != null) {
                positions.add(MarblePosition.from(getArea()[i], isMovable.contains(getArea()[i]), i));
            }
        }
        return positions;
    }

    int getMarblePositionOnTable(Marble marble);

    Marble[] getArea();
}
