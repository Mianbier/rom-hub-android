/*
 * This file is part of HyperCeiler.
 *
 * HyperCeiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2023-2026 HyperCeiler Contributions
 */

package org.linbaogu.romhub.hc.util;




import java.time.LocalDate;

public class PersistConfig {
    private static final LocalDate localDate = LocalDate.now();



    public final static boolean isNeedGrayView = false;
    public final static boolean isLunarNewYearThemeView = false;

    public final static boolean isAprilFoolsThemeView = localDate.getMonthValue() == 4 && localDate.getDayOfMonth() == 1;
}
