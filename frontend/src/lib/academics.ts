import type { AttendanceRow, Grade, GradeRow } from "../api/types";
const points: Record<Grade, number> = { "A+": 10, A: 9, "B+": 8, B: 7, "C+": 6, C: 5, D: 4, F: 0 };
export function attendanceRow(courseCode: string, courseTitle: string, held: number, attended: number, credited: number): AttendanceRow {
  const percentage = held === 0 ? 100 : Math.round(((attended + credited) / held) * 1000) / 10;
  const classesNeededFor75 = percentage >= 75 ? 0 : Math.max(0, Math.ceil((0.75 * held - (attended + credited)) / 0.25));
  return { courseCode, courseTitle, classesHeld: held, classesAttended: attended, dutyLeaveCredited: credited, percentage, shortage: percentage < 75, classesNeededFor75 };
}
export function gradePoint(grade: Grade | null): number | null { return grade === null ? null : points[grade]; }
export function sgpa(grades: GradeRow[]): number | null {
  const published = grades.filter((g) => g.grade !== null);
  const credits = published.reduce((sum, g) => sum + g.credits, 0);
  return credits ? Math.round((published.reduce((sum, g) => sum + g.credits * (g.gradePoint ?? 0), 0) / credits) * 100) / 100 : null;
}
