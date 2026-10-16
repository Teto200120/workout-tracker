import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import test from "node:test";

for (const zone of ["America/New_York", "Asia/Tokyo", "Pacific/Auckland"]) {
  test(`calendar and streak stay local across midnight and DST in ${zone}`, () => {
    const probe = `
      import assert from 'node:assert/strict';
      import {dateKeyFromDate,getWorkoutStreak,mondayFirstWeekDates} from './src/js/domain/schedule.js';
      for (const now of [new Date(2026,9,16,23,30),new Date(2026,2,8,0,30),new Date(2026,10,1,23,30)]) {
        const key = [now.getFullYear(),String(now.getMonth()+1).padStart(2,'0'),String(now.getDate()).padStart(2,'0')].join('-');
        assert.equal(dateKeyFromDate(now),key);
        const yesterday = new Date(now); yesterday.setDate(now.getDate()-1);
        assert.equal(getWorkoutStreak([{date:key},{date:dateKeyFromDate(yesterday)}],now),2);
        const week=mondayFirstWeekDates(now);
        assert.equal(week[0].getDay(),1);
        assert.equal(week[6].getDay(),0);
      }
    `;
    execFileSync(process.execPath, ["--input-type=module", "-e", probe], {
      env: { ...process.env, TZ: zone },
    });
    assert.ok(true);
  });
}
