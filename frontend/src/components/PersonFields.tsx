// Gender, birth date and phone: needed for contact names ("Мама", "Старший брат") and quick dial
export interface PersonData {
  gender: string;
  birth_date: string;
  phone_number: string;
}

const inputClass =
  "w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500";

export function PersonFields({
  value,
  onChange,
}: {
  value: PersonData;
  onChange: (patch: Partial<PersonData>) => void;
}) {
  return (
    <>
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Пол</label>
          <select
            required
            value={value.gender}
            onChange={(e) => onChange({ gender: e.target.value })}
            className={inputClass}
          >
            <option value="" disabled>
              Выберите
            </option>
            <option value="male">Мужской</option>
            <option value="female">Женский</option>
          </select>
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Дата рождения</label>
          <input
            type="date"
            required
            max={new Date().toISOString().slice(0, 10)}
            value={value.birth_date}
            onChange={(e) => onChange({ birth_date: e.target.value })}
            className={inputClass}
          />
        </div>
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Телефон</label>
        <input
          type="tel"
          required
          placeholder="+998901234567"
          value={value.phone_number}
          onChange={(e) => onChange({ phone_number: e.target.value })}
          className={inputClass}
        />
      </div>
    </>
  );
}
